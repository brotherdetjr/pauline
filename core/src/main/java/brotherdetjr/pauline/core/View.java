package brotherdetjr.pauline.core;

import brotherdetjr.pauline.events.Event;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@FunctionalInterface
public interface View<State, Renderer, E extends Event> {
	void render(Context<State, Renderer, E> context);

	@Getter
	@RequiredArgsConstructor
	class Context<State, Renderer, E> {
		private final State state;
		private final Renderer renderer;
		private final E event;

		public static <State, Renderer, E> Context<State, Renderer, E> of(
			State state,
			Renderer renderer,
			E event) {
			return new Context<>(state, renderer, event);
		}
	}
}
