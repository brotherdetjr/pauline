package brotherdetjr.pauline.core;

import brotherdetjr.pauline.events.Event;
import brotherdetjr.pauline.events.EventSource;
import com.google.common.util.concurrent.Striped;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static brotherdetjr.utils.Utils.checkNotNull;
import static brotherdetjr.utils.Utils.propagateIfError;
import static brotherdetjr.utils.Utils.searchInHierarchy;
import static com.google.common.base.Throwables.getStackTraceAsString;
import static com.google.common.collect.Maps.newConcurrentMap;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public class Flow<Renderer, E extends Event> {
	private final EventSource<E> eventSource;
	private final Dispatcher<E> dispatcher;
	private final View<Throwable, Renderer, E> failView;
	private final Executor executor;
	private final SessionStorage sessionStorage;
	private final Function<E, Renderer> rendererFactory;
	private final Striped<Lock> striped;
	private final Logger log;

	public Flow(EventSource<E> eventSource,
				Dispatcher<E> dispatcher,
				View<Throwable, Renderer, E> failView,
				Executor executor,
				SessionStorage sessionStorage,
				int stripes,
				Function<E, Renderer> rendererFactory,
				Logger log) {
		this.eventSource = eventSource;
		this.dispatcher = dispatcher;
		this.failView = failView;
		this.executor = executor;
		this.sessionStorage = sessionStorage;
		this.rendererFactory = rendererFactory;
		striped = Striped.lock(stripes);
		this.log = log;
	}

	public void init() {
		eventSource.onEvent(this::handleInExecutor);
	}

	private void handleInExecutor(E event) {
		try {
			log.debug("Received event {}", event);
			executor.execute(() -> {
				try {
					handle(event);
				} catch (Throwable ex) {
					log.error("Failed to process event {}: {}", event, getStackTraceAsString(ex));
					propagateIfError(ex);
					renderFail(ex, event);
				}
			});
		} catch (Throwable ex) {
			log.error("Failed to execute event handling. Event: {}. Cause: {}", event, getStackTraceAsString(ex));
			propagateIfError(ex);
			renderFail(ex, event);
		}
	}

	private void renderFail(Throwable ex, E event) {
		try {
			failView.render(View.Context.of(ex, rendererFactory.apply(event), event));
		} catch (Throwable ex2) {
			log.error("Failed to process event {} and to render it: {}", event, getStackTraceAsString(ex2));
			propagateIfError(ex2);
		}
	}

	private void handle(E event) {
		long sessionId = event.getSessionId();
		synched(sessionId, session -> {
			if (session != null) {
				processIfNotBusy(event);
			} else {
				log.debug("Registering session {}", sessionId);
				initSessionAndProcess(event);
			}
		});
	}

	private void processIfNotBusy(E event) {
		try {
			sessionStorage
				.acquireLock(event.getSessionId())
				.whenComplete((ignore, ex) -> onLockAcquired(event, ex));
		} catch (Throwable ex) {
			handleLockAcquisitionFailure(ex, event);
		}
	}

	private void onLockAcquired(E event, Throwable ex) {
		if (ex == null) {
			try {
				sessionStorage.getStateAndVars(event.getSessionId())
					.whenComplete((state, ex1) -> onStateAndVarsRetrieved(event, state, ex1));
			} catch (Throwable ex1) {
				handleStateAndVarsRetrievalFailure(ex1, event);
			}
		} else {
			handleLockAcquisitionFailure(ex, event);
		}
	}

	private void handleLockAcquisitionFailure(Throwable ex, E event) {
		long sessionId = event.getSessionId();
		if (ex instanceof SpamException) {
			log.error("Looks like somebody is spamming us. Event queue size: {}, sessionID: {}",
				((SpamException) ex).getEventQueue().size(), sessionId);
			renderFail(new IllegalStateException("Wait, not so fast!"), event);
		} else {
			log.error("Failed to acquire session lock. Session ID: " + sessionId, ex);
			renderFail(ex, event);
		}
	}

	private <T> void onStateAndVarsRetrieved(E event, Pair<T, Map<String, ?>> stateAndVars, Throwable ex) {
		long sessionId = event.getSessionId();
		if (ex == null) {
			try {
				Session<T> session = Session.of(sessionId, stateAndVars);
				Controller<T, ?, E> controller = dispatcher.dispatch(event, session);
				whenCompleteTransition(event, controller.transit(event, session));
			} catch (Throwable ex1) {
				handleTransitionFailure(ex1, event);
			}
		} else {
			handleStateAndVarsRetrievalFailure(ex, event);
		}
	}

	private void handleStateAndVarsRetrievalFailure(Throwable ex, E event) {
		long sessionId = event.getSessionId();
		try {
			log.error("Failed to retrieve session state/vars. Session ID: " + sessionId, ex);
			renderFail(ex, event);
		} finally {
			releaseLock(sessionId);
		}
	}

	private void releaseLock(long sessionId) {
		sessionStorage.releaseLock(sessionId)
			.whenComplete((ignore, ex) -> log.error("Failed to release session lock. Session id: " + sessionId, ex));
	}

	private Session initSessionAndProcess(E event) {
		Session session = new Session(null, true);
		sessions.put(event.getSessionId(), session);
		process(event, dispatcher.dispatch(event).transit(event));
		return session;
	}

	private void whenCompleteTransition(E event, CompletableFuture<? extends ViewAndSession<?, Renderer, E>> future) {
		future.whenComplete((viewAndState, ex) -> executor.execute(() -> {
			if (ex == null) {
				freeSessionAndRender(event, viewAndState);
			} else {
				handleTransitionFailure(ex, event);
			}
		}));
	}

	private void handleTransitionFailure(Throwable ex, E event) {
		try {
			log.error("Failed to perform transition by event {}. Cause: {}", event, getStackTraceAsString(ex));
			renderFail(ex, event);
		} finally {
			releaseLock(event.getSessionId());
		}
	}

	private void freeSessionAndRender(E event, ViewAndSession<?, Renderer, E> viewAndSession) {
		synched(event.getSessionId(), ignore -> {
			try {
				freeSession(event, viewAndSession);
				viewAndSession.render(rendererFactory.apply(event), event);
			} catch (Throwable ex) {
				log.error("Failed to render view. Event: {}. Cause: {}", event, getStackTraceAsString(ex));
				propagateIfError(ex);
				renderFail(ex, event);
			}
		});
	}

	private void freeSession(E event, ViewAndSession<?, Renderer, E> viewAndSession) {
		Session session = sessions.get(event.getSessionId());
		session.setState(viewAndSession.getState());
		session.setBusy(false);
		log.debug(
			"Set new state for session {}: {}. View key: {}",
			event.getSessionId(),
			viewAndSession.getState(),
			viewAndSession.getState().getClass().getName()
		);
	}

	private void synched(Long userId, Consumer<Session> consumer) {
		Lock lock = striped.get(userId);
		try {
			lock.lock();
			consumer.accept(sessions.get(userId));
		} finally {
			lock.unlock();
		}
	}

	@RequiredArgsConstructor
	public static class Builder<Renderer, E extends Event> {
		private EventSource<E> eventSource;
		private Map<Class<?>, View<?, Renderer, E>> views = newHashMap();
		private ControllerRegistry<E> controllers = new ControllerRegistry<>();
		private View<Throwable, Renderer, E> failView;
		private Executor executor = directExecutor();
		private Map<Long, Session> sessions = newConcurrentMap();
		private int stripes = 1000;
		private Function<E, Renderer> rendererFactory;
		private Logger log = LoggerFactory.getLogger(Flow.class);

		private Controller<?, ?, E> initial;

		@RequiredArgsConstructor
		public class Handle<E1 extends E> {
			private final Class<E1> eventClass;

			public <From> Builder<Renderer, E> with(BiFunction<E1, From, CompletableFuture<?>> func) {
				return new When<From>().with(func);
			}

			public Builder<Renderer, E> with(Function<E1, CompletableFuture<?>> func) {
				return with((event, ignore) -> func.apply(event));
			}

			public <From> Builder<Renderer, E> by(BiFunction<E1, From, CompletableFuture<?>> func) {
				return with(func);
			}

			public <From> When<From> when(From state) {
				return new When<>(state);
			}

			public <From> When<From> when(Class<From> stateClass) {
				return new When<>(stateClass);
			}

			public class When<From> {
				private final From state;
				private final Class<From> stateClass;

				public When(From state) {
					this.state = state;
					stateClass = null;
				}

				public When(Class<From> stateClass) {
					this.stateClass = stateClass;
					state = null;
				}

				public When() {
					state = null;
					stateClass = null;
				}

				@SuppressWarnings("unchecked")
				public <To> Builder<Renderer, E> with(BiFunction<E1, From, CompletableFuture<?>> func) {
					Controller<From, To, E1> controller = new Controller<From, To, E1>() {
						@Override
						public <R> CompletableFuture<ViewAndSession<To, R, E1>> transit(E1 e, From s) {
							return toViewAndState((CompletableFuture<To>) func.apply(e, s));
						}
					};
					Class<E1> eventClass = Handle.this.eventClass;
					if (state != null) {
						controllers.put(eventClass, state, controller);
					} else if (stateClass != null) {
						controllers.put(eventClass, stateClass, controller);
					} else {
						controllers.put(eventClass, controller);
					}
					return Builder.this;
				}

				public Builder<Renderer, E> with(Function<E1, CompletableFuture<?>> func) {
					return with((event, ignore) -> func.apply(event));
				}

				public Builder<Renderer, E> by(BiFunction<E1, From, CompletableFuture<?>> func) {
					return with(func);
				}

			}
		}

		@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
		@RequiredArgsConstructor
		public class Render<State> {
			private final Collection<Class<? extends State>> keys;

			public Builder<Renderer, E> as(View<State, Renderer, E> view) {
				keys.forEach(key -> views.put(key, view));
				return Builder.this;
			}
		}

		public <E1 extends E> Handle<E1> handle(Class<E1> eventClass) {
			return new Handle<>(eventClass);
		}

		@SuppressWarnings("unchecked")
		public <E1 extends E> Handle<E1> handle() {
			return new Handle<>((Class<E1>) Event.class);
		}

		public <To> Builder<Renderer, E> initialController(Controller<Void, To, E> initial) {
			this.initial = initial;
			return this;
		}

		public <To> Builder<Renderer, E> initial(Function<E, CompletableFuture<To>> func) {
			return initialController(new Controller<Void, To, E>() {
				@Override
				public <R> CompletableFuture<ViewAndSession<To, R, E>> transit(E e, Void s) {
					return toViewAndState(func.apply(e));
				}
			});
		}

		public <To> Builder<Renderer, E> initial(
			Function<E, CompletableFuture<To>> func,
			@SuppressWarnings("UnusedParameters") Class<To> probe) {
			return initial(func);
		}

		@SafeVarargs
		public final <S> Render<S> render(Class<? extends S>... keys) {
			return new Render<>(asList(keys));
		}

		public Builder<Renderer, E> eventSource(EventSource<E> eventSource) {
			this.eventSource = eventSource;
			return this;
		}

		public Builder<Renderer, E> failView(View<Throwable, Renderer, E> failView) {
			this.failView = failView;
			return this;
		}

		public Builder<Renderer, E> executor(Executor executor) {
			this.executor = executor;
			return this;
		}

		@SuppressWarnings("unused")
		public Builder<Renderer, E> sessions(Map<Long, Session> sessions) {
			this.sessions = sessions;
			return this;
		}

		@SuppressWarnings("unused")
		public Builder<Renderer, E> stripes(int stripes) {
			this.stripes = stripes;
			return this;
		}

		public Builder<Renderer, E> rendererFactory(Function<E, Renderer> rendererFactory) {
			this.rendererFactory = rendererFactory;
			return this;
		}

		public Builder<Renderer, E> log(Logger log) {
			this.log = log;
			return this;
		}

		public Flow<Renderer, E> build(boolean initialized) {
			checkNotNull(rendererFactory, eventSource, initial, failView);
			Flow<Renderer, E> flow = new Flow<>(
				eventSource,
				newDispatcher(),
				failView,
				executor,
				sessions,
				stripes,
				rendererFactory,
				log
			);
			if (initialized) {
				flow.init();
			}
			return flow;
		}

		public Flow<Renderer, E> build() {
			return build(true);
		}

		private Dispatcher<E> newDispatcher() {
			return new Dispatcher<E>() {
				@SuppressWarnings("unchecked")
				@Override
				public <From, To> Controller<From, To, E> dispatch(E event, From state) {
					if (state != null) {
						return requireNonNull(
							controllers.get((Class<E>) event.getClass(), state),
							() -> "No controller registered for state " + state +
								" and event class " + event.getClass().getName()
						);
					} else {
						return (Controller<From, To, E>) initial;
					}
				}
			};
		}

		@SuppressWarnings("unchecked")
		private <To, R, E1 extends E> CompletableFuture<ViewAndSession<To, R, E1>> toViewAndState(
			CompletableFuture<To> future) {
			return future.thenApply(n ->
				ViewAndSession.of(
					ofNullable(
						searchInHierarchy(n.getClass(), c -> (View<To, R, E1>) views.get(c))
					).orElseThrow(() -> new IllegalStateException(
						"No view defined for state class " + n.getClass().getName())
					),
					n
				)
			);
		}
	}
}
