package brotherdetjr.pauline.core;

import brotherdetjr.pauline.events.Event;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface View<Renderer, E extends Event> {
	<State> CompletableFuture<Void> render(Context<State, Renderer, E> context);

	@RequiredArgsConstructor
	class Context<State, Renderer, E> {
		private final Session<State> session;
		@Getter
		private final Renderer renderer;
		@Getter
		private final E event;
		@Getter
		private final Throwable throwable;

		public State getState() {
			return session.getState();
		}

		public <T> T getVar(String key, Class<T> probe) {
			return session.getVar(key, probe);
		}

		public static <State, Renderer, E> Context<State, Renderer, E>
		of(Session<State> session, Renderer renderer, E event, Throwable throwable) {
			return new Context<>(session, renderer, event, throwable);
		}
	}
}
