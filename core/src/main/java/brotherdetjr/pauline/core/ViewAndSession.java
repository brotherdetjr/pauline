package brotherdetjr.pauline.core;

import brotherdetjr.pauline.events.Event;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Getter
public class ViewAndSession<State, Renderer, E extends Event> {
	private final View<Renderer, E> view;
	private final Session<State> session;

	public static <S, R, E extends Event> ViewAndSession<S, R, E> of(View<R, E> view, Session<S> session) {
		return new ViewAndSession<>(view, session);
	}

	public CompletableFuture<Void> render(Renderer renderer, E event, Throwable ex) {
		return view.render(View.Context.of(session, renderer, event, ex));
	}

	public CompletableFuture<Void> render(Renderer renderer, E event) {
		return render(renderer, event, null);
	}
}
