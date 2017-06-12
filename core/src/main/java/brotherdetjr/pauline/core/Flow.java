package brotherdetjr.pauline.core;

import brotherdetjr.pauline.events.Event;
import brotherdetjr.pauline.events.EventSource;
import brotherdetjr.utils.futures.FailureHandler;
import brotherdetjr.utils.futures.FutureFunction;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;

import static brotherdetjr.utils.Utils.checkNotNull;
import static brotherdetjr.utils.Utils.searchInHierarchy;
import static brotherdetjr.utils.futures.Safely.immediately;
import static brotherdetjr.utils.futures.Safely.noFailureExpectedHere;
import static brotherdetjr.utils.futures.Safely.safely;
import static com.google.common.base.Throwables.getStackTraceAsString;
import static com.google.common.collect.Maps.newConcurrentMap;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.slf4j.LoggerFactory.getLogger;

public class Flow<Renderer, E extends Event> {
	private final EventSource<E> eventSource;
	private final Dispatcher<E> dispatcher;
	private final View<Renderer, E> failView;
	private final Executor executor;
	private final SessionStorage sessionStorage;
	private final Function<E, Renderer> rendererFactory;
	private final boolean allowUnlockedRendering;
	private final Logger log;

	public Flow(EventSource<E> eventSource,
				Dispatcher<E> dispatcher,
				View<Renderer, E> failView,
				Executor executor,
				SessionStorage sessionStorage,
				boolean allowUnlockedRendering,
				Function<E, Renderer> rendererFactory,
				Logger log) {
		this.eventSource = eventSource;
		this.dispatcher = dispatcher;
		this.failView = failView;
		this.executor = executor;
		this.sessionStorage = sessionStorage;
		this.rendererFactory = rendererFactory;
		this.allowUnlockedRendering = allowUnlockedRendering;
		this.log = log;
	}

	public void init() {
		eventSource.onEvent((event) -> {
			try {
				log.debug("Received event: {}", event);
				executor.execute(() -> process(event));
			} catch (Exception ex) {
				log.error("Failed to process event: {}. Cause: {}", event, getStackTraceAsString(ex));
				new EventContext(event).renderFailureNoLock(ex);
			}
		});
	}

	private void process(E event) {
		EventContext ctx = new EventContext(event);
		completedFuture(of(event.getSessionId()))
			.thenCompose(safely(sessionStorage::acquireLock, ctx.handleLockAcquisitionFailure()))
			.thenCompose(safely(sessionStorage::getStateAndVars, ctx.handleStateAndVarsRetrievalFailure()))
			.thenCompose(immediately(sv -> Session.of(event.getSessionId(), sv), noFailureExpectedHere()))
			.thenCompose(safely(ctx.transit(), ctx.handleTransitionFailure()))
			.thenCompose(safely(ctx.storeSession(), ctx.handleSessionStorageFailure()))
			.thenCompose(safely(ctx.renderView(), ctx.handleRenderingFailure()))
			.thenCompose(safely(ignore -> ctx.releaseLock(), noFailureExpectedHere()));
	}

	private class EventContext {
		private final E event;

		public EventContext(E event) {
			this.event = event;
		}

		public <T> FutureFunction<Session<T>, ViewAndSession<Object, Renderer, E>> transit() {
			return session -> dispatcher.dispatch(event, session).transit(event, session);
		}

		public <T> FutureFunction<ViewAndSession<T, Renderer, E>, ViewAndSession<T, Renderer, E>> storeSession() {
			return vs -> sessionStorage
				.store(vs.getSession().getState(), vs.getSession().getChangedVars())
				.thenApply(ignore -> vs);
		}

		public <T> FutureFunction<ViewAndSession<T, Renderer, E>, Void> renderView() {
			return vs -> {
				log.debug("Set new state for. Session ID: {}. State: {}",
					event.getSessionId(), vs.getSession().getState());
				return vs.render(rendererFactory.apply(event), event).thenApply(ignore -> null);
			};
		}

		public FailureHandler<Long> handleLockAcquisitionFailure() {
			return (ex, sessionId) -> {
				logError("Failed to acquire session lock", ex);
				renderFailureNoLock(ex);
			};
		}

		public FailureHandler<Long> handleStateAndVarsRetrievalFailure() {
			return (ex, sessionId) -> {
				logError("Failed to retrieve session state/vars", ex);
				renderFailureAndReleaseLock(ex, null);
			};
		}

		public <T> FailureHandler<Session<T>> handleTransitionFailure() {
			return (ex, session) -> {
				logError("Failed to perform transition", ex);
				renderFailureAndReleaseLock(ex, session);
			};
		}

		public <T> FailureHandler<ViewAndSession<T, Renderer, E>> handleSessionStorageFailure() {
			return (ex, vs) -> {
				logError("Failed to store session", ex);
				renderFailureAndReleaseLock(ex, vs.getSession());
			};
		}

		public <T> FailureHandler<ViewAndSession<T, Renderer, E>> handleRenderingFailure() {
			return (ex, vs) -> {
				logError("Failed to render view", ex);
				renderFailureAndReleaseLock(ex, vs.getSession());
			};
		}

		private void logError(String message, Throwable ex) {
			log.error("{}. Event: {}. Cause: {}", message, event, getStackTraceAsString(ex));
		}

		private void renderFailureNoLock(Throwable ex) {
			if (allowUnlockedRendering) {
				renderFailure(ex, null).whenComplete((ignore, ex1) -> {
					if (ex1 != null) {
						log.error("Failed to render: {}. Event: {}. Cause: {}",
							ex, event, getStackTraceAsString(ex1));
					}
				});
			}
		}

		private <From> void renderFailureAndReleaseLock(Throwable ex, Session<From> session) {
			try {
				renderFailure(ex, session).whenComplete((ignore, ex1) -> {
					if (ex1 != null) {
						log.error("Failed to render: {}. Event: {}. Cause: {}",
							ex, event, getStackTraceAsString(ex1));
					}
					releaseLock();
				});
			} catch (Exception ex1) {
				log.error("Failed to render: {}. Event: {}. Cause: {}",
					ex, event, getStackTraceAsString(ex1));
				releaseLock();
			}
		}

		private <State> CompletableFuture<Void> renderFailure(Throwable ex, Session<State> session) {
			return failView.render(View.Context.of(session, rendererFactory.apply(event), event, ex));
		}

		private CompletableFuture<Void> releaseLock() {
			long sessionId = event.getSessionId();
			log.debug("Releasing session lock. Session ID: {}", sessionId);
			return sessionStorage.releaseLock(sessionId)
				.whenComplete((ignore, ex) -> {
					if (ex != null) {
						log.error("Failed to release session lock. Session ID: {}. Cause: {}",
							sessionId, getStackTraceAsString(ex));
					}
				});
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
		private Logger log = getLogger(Flow.class);

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
