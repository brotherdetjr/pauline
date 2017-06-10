package brotherdetjr.pauline.core;

import brotherdetjr.pauline.events.Event;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class ViewAndSession<State, Renderer, E extends Event> {
	private final View<State, Renderer, E> view;
	private final Session<State> session;

	public static <S, R, E extends Event> ViewAndSession<S, R, E> of(View<S, R, E> view, Session<S> session) {
		return new ViewAndSession<>(view, session);
	}

	public void render(Renderer renderer, E event) {
		view.render(View.Context.of(session, renderer, event));
	}
}
