package puremvc.core;

import com.google.common.util.concurrent.Striped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import puremvc.core.Controller.ViewAndState;

import java.beans.ConstructorProperties;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.google.common.base.Throwables.getStackTraceAsString;
import static com.google.common.base.Throwables.propagateIfPossible;
import static com.google.common.collect.Maps.newConcurrentMap;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static brotherdetjr.utils.Utils.checkNotNull;

@Slf4j
public class Mvc<Renderer, E extends Event> {
	private final EventSource<E> eventSource;
	private final Dispatcher<E> dispatcher;
	private final View<Throwable, Renderer, E> failView;
	private final Executor executor;
	private final Map<Long, Session> sessions;
	private final Renderer renderer;
	private final Striped<Lock> striped;

	@ConstructorProperties({"eventSource", "dispatcher", "failView", "executor", "sessions", "renderer"})
	public Mvc(EventSource<E> eventSource,
			   Dispatcher<E> dispatcher,
			   View<Throwable, Renderer, E> failView,
			   Executor executor,
			   Map<Long, Session> sessions,
			   int stripes,
			   Renderer renderer) {
		this.eventSource = eventSource;
		this.dispatcher = dispatcher;
		this.failView = failView;
		this.executor = executor;
		this.sessions = sessions;
		this.renderer = renderer;
		striped = Striped.lock(stripes);
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
					propagateIfPossible(ex, Error.class);
					renderFail(ex, event);
				}
			});
		} catch (Throwable ex) {
			log.error("Failed to execute event handling. Event: {}. Cause: {}", event, getStackTraceAsString(ex));
			propagateIfPossible(ex, Error.class);
			renderFail(ex, event);
		}
	}

	private void renderFail(Throwable ex, E event) {
		try {
			failView.render(View.Context.of(ex, renderer, event));
		} catch (Throwable ex2) {
			log.error("Failed to process event {} and to render it: {}", event, getStackTraceAsString(ex2));
			propagateIfPossible(ex2, Error.class);
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
			session.setBusy(true);
			process(event, dispatcher.dispatch(event, session.getState()).transit(event, session.getState()));
		} else {
			log.error("Looks like somebody spamming us. Event: {}", event);
			renderFail(new IllegalStateException("Wait, not so fast!"), event);
		}
	}

	private Session initSessionAndProcess(E event) {
		Session session = new Session(null, true);
		long sessionId = event.getSessionId();
		sessions.put(sessionId, session);
		log.debug("Initialized session: {}", sessionId);
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
				propagateIfPossible(ex, Error.class);
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
		private Map<Class<?>, Controller<?, ?, E>> controllers = newHashMap();
		private View<Throwable, Renderer, E> failView;
		private Executor executor = directExecutor();
		private Map<Long, Session> sessions = newConcurrentMap();
		private int stripes = 1000;
		private Renderer renderer;

		private Controller<?, ?, E> initial;

		public <From, To> Builder<Renderer, E> rawController(Class<From> from, Controller<From, To, E> controller) {
			controllers.put(from, controller);
			return this;
		}

		public <From, To> Builder<Renderer, E> controller(
			Class<From> from,
			BiFunction<E, From, CompletableFuture<To>> func) {
			return rawController(from, new Controller<From, To, E>() {
				@Override
				public <R> CompletableFuture<ViewAndState<To, R, E>> transit(E e, From s) {
					return toViewAndState(func.apply(e, s));
				}
			});
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

		public <To> Builder<Renderer, E> view(Class<To> key, View<To, Renderer, E> view) {
			views.put(key, view);
			return this;
		}

		@SuppressWarnings("unchecked")
		@SafeVarargs
		public final <To> Builder<Renderer, E> view(View<To, Renderer, E> view, Class<? extends To>... keys) {
			for (Class<? extends To> key : keys) {
				view((Class<To>) key, view);
			}
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

		public Builder<Renderer, E> sessions(Map<Long, Session> sessions) {
			this.sessions = sessions;
			return this;
		}

		public Builder<Renderer, E> stripes(int stripes) {
			this.stripes = stripes;
			return this;
		}

		public Builder<Renderer, E> renderer(Renderer renderer) {
			this.renderer = renderer;
			return this;
		}

		public Mvc<Renderer, E> build(boolean initialized) {
			checkNotNull(renderer, controllers, initial, failView);
			Mvc<Renderer, E> mvc = new Mvc<>(
				eventSource,
				newDispatcher(),
				failView,
				executor,
				sessions,
				stripes,
				renderer
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
						Controller<?, ?, E> controller = controllers.get(state.getClass());
						if (controller == null) {
							throw new IllegalArgumentException(
								"No controller registered for state type " + state.getClass()
							);
						}
						return (Controller<From, ?, E>) controller;
					} else {
						return (Controller<From, ?, E>) initial;
					}
				}
			};
		}

		@SuppressWarnings("unchecked")
		private <To, R> CompletableFuture<ViewAndState<To, R, E>> toViewAndState(CompletableFuture<To> future) {
			return future.thenApply(n -> ViewAndState.of((View<To, R, E>) views.get(n.getClass()), n));
		}
	}
}
