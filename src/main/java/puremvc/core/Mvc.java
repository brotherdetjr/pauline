package puremvc.core;

import com.google.common.util.concurrent.Striped;
import lombok.RequiredArgsConstructor;
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
import static com.google.common.base.Throwables.getStackTraceAsString;
import static com.google.common.collect.Maps.newConcurrentMap;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

public class Mvc<Renderer, E extends Event> {
	private final EventSource<E> eventSource;
	private final Dispatcher<E> dispatcher;
	private final View<Throwable, Renderer, E> failView;
	private final Executor executor;
	private final Map<Long, Session> sessions;
	private final Renderer renderer;
	private final Striped<Lock> striped;
	private final Logger log;

	public Mvc(EventSource<E> eventSource,
			   Dispatcher<E> dispatcher,
			   View<Throwable, Renderer, E> failView,
			   Executor executor,
			   Map<Long, Session> sessions,
			   int stripes,
			   Renderer renderer,
			   Logger log) {
		this.eventSource = eventSource;
		this.dispatcher = dispatcher;
		this.failView = failView;
		this.executor = executor;
		this.sessions = sessions;
		this.renderer = renderer;
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
			failView.render(View.Context.of(ex, renderer, event));
		} catch (Throwable ex2) {
			log.error("Failed to process event {} and to render it: {}", event, getStackTraceAsString(ex2));
			propagateIfError(ex2);
		}
	}

	private void handle(E event) {
		Long sessionId = event.getSessionId();
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
		Session session = sessions.get(event.getSessionId());
		if (!session.isBusy()) {
			Controller<Object, ?, E> controller = dispatcher.dispatch(event, session.getState());
			if (controller != null) {
				session.setBusy(true);
				process(event, controller.transit(event, session.getState()));
			} else {
				log.debug("Filtered out by guard: {}, {}", event, session.getState());
			}
		} else {
			log.error("Looks like somebody spamming us. Event: {}", event);
			renderFail(new IllegalStateException("Wait, not so fast!"), event);
		}
	}

	private Session initSessionAndProcess(E event) {
		Session session = new Session(null, true);
		long sessionId = event.getSessionId();
		sessions.put(sessionId, session);
		process(event, dispatcher.dispatch(event).transit(event));
		return session;
	}

	private void process(E event, CompletableFuture<? extends ViewAndState<?, Renderer, E>> future) {
		future.whenComplete((viewAndState, ex) -> executor.execute(() -> {
			if (ex == null) {
				freeSessionAndRender(event, viewAndState);
			} else {
				log.error("Failed to perform transition by event {}. Cause: {}", event, getStackTraceAsString(ex));
				renderFail(ex, event);
			}
		}));
	}

	private void freeSessionAndRender(E event, ViewAndState<?, Renderer, E> viewAndState) {
		synched(event.getSessionId(), ignore -> {
			try {
				freeSession(event, viewAndState);
				viewAndState.render(renderer, event);
			} catch (Throwable ex) {
				log.error("Failed to render view. Event: {}. Cause: {}", event, getStackTraceAsString(ex));
				propagateIfError(ex);
				renderFail(ex, event);
			}
		});
	}

	private void freeSession(E event, ViewAndState<?, Renderer, E> viewAndState) {
		Session session = sessions.get(event.getSessionId());
		session.setState(viewAndState.getState());
		session.setBusy(false);
		log.debug(
			"Set new state for session {}: {}. View key: {}",
			event.getSessionId(),
			viewAndState.getState(),
			viewAndState.getState().getClass().getName()
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
		private final EventSource<E> eventSource;
		private Map<Class<?>, View<?, Renderer, E>> views = newHashMap();
		private ControllerRegistry<E> controllers = new ControllerRegistry<>();
		private View<Throwable, Renderer, E> failView;
		private Executor executor = directExecutor();
		private Map<Long, Session> sessions = newConcurrentMap();
		private int stripes = 1000;
		private Renderer renderer;
		private Logger log = LoggerFactory.getLogger(Mvc.class);

		private Controller<?, ?, E> initial;

		@RequiredArgsConstructor
		public class Handle<E1 extends E> {
			private final Class<? extends Event> eventClass;

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
						public <R> CompletableFuture<ViewAndState<To, R, E1>> transit(E1 e, From s) {
							return toViewAndState((CompletableFuture<To>) func.apply(e, s));
						}
					};
					Class<E> eventClass = (Class<E>) Handle.this.eventClass;
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
			return new Handle<E1>(eventClass);
		}

		@SuppressWarnings("unchecked")
		public <E1 extends E> Handle<E1> handle() {
			return new Handle<E1>(Event.class);
		}

		public <To> Builder<Renderer, E> initialController(Controller<Void, To, E> initial) {
			this.initial = initial;
			return this;
		}

		public <To> Builder<Renderer, E> initial(Function<E, CompletableFuture<To>> func) {
			return initialController(new Controller<Void, To, E>() {
				@Override
				public <R> CompletableFuture<ViewAndState<To, R, E>> transit(E e, Void s) {
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

		public Builder<Renderer, E> renderer(Renderer renderer) {
			this.renderer = renderer;
			return this;
		}

		public Builder<Renderer, E> log(Logger log) {
			this.log = log;
			return this;
		}

		public Mvc<Renderer, E> build(boolean initialized) {
			checkNotNull(renderer, initial, failView);
			Mvc<Renderer, E> mvc = new Mvc<>(
				eventSource,
				newDispatcher(),
				failView,
				executor,
				sessions,
				stripes,
				renderer,
				log
			);
			if (initialized) {
				mvc.init();
			}
			return mvc;
		}

		public Mvc<Renderer, E> build() {
			return build(true);
		}

		private Dispatcher<E> newDispatcher() {
			return new Dispatcher<E>() {
				@SuppressWarnings("unchecked")
				@Override
				public <From> Controller<From, ?, E> dispatch(E event, From state) {
					if (state != null) {
						return requireNonNull(controllers.get(event.getClass(), state),
							() -> "No controller registered for state " + state +
								" and event class " + event.getClass().getName());
					} else {
						return (Controller<From, ?, E>) initial;
					}
				}
			};
		}

		@SuppressWarnings("unchecked")
		private <To, R, E1 extends E> CompletableFuture<ViewAndState<To, R, E1>> toViewAndState(
			CompletableFuture<To> future) {
			return future.thenApply(n -> ViewAndState.of((View<To, R, E1>) views.get(n.getClass()), n));
		}
	}
}
