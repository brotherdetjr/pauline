package brotherdetjr.pauline.core;

import brotherdetjr.pauline.events.Event;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface Controller<From, To, E extends Event> {

	<Renderer> CompletableFuture<ViewAndState<To, Renderer, E>> transit(E event, Session<From> session);

	default <Renderer> CompletableFuture<ViewAndState<To, Renderer, E>> transit(E event) {
		return transit(event, null);
	}

}
