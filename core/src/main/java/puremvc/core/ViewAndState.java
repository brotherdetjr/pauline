package puremvc.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class ViewAndState<State, Renderer, E extends Event> {
	private final View<State, Renderer, E> view;
	private final State state;

	public static <S, R, E extends Event> ViewAndState<S, R, E> of(View<S, R, E> view, S state) {
		return new ViewAndState<>(view, state);
	}

	public void render(Renderer renderer, E event) {
		view.render(View.Context.of(state, renderer, event));
	}
}
