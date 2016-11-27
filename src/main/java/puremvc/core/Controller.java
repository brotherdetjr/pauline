package puremvc.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface Controller<From, To, E extends Event> {

	<Renderer> CompletableFuture<ViewAndState<To, Renderer, E>> transit(E event, From state);

	default <Renderer> CompletableFuture<ViewAndState<To, Renderer, E>> transit(E event) {
		return transit(event, null);
	}

	@RequiredArgsConstructor
	@Getter
	class ViewAndState<State, Renderer, E extends Event> {
		private final View<State, Renderer, E> view;
		private final State state;

		public static <S, R, E extends Event> ViewAndState<S, R, E> of(View<S, R, E> view, S state) {
			return new ViewAndState<>(view, state);
		}

		public void render(Renderer renderer, E event) {
			view.render(View.Context.of(state, renderer, event));
		}
	}
}
