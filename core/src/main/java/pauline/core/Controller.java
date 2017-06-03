package pauline.core;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface Controller<From, To, E> {

	<Renderer> CompletableFuture<ViewAndState<To, Renderer, E>> transit(E event, From state);

	default <Renderer> CompletableFuture<ViewAndState<To, Renderer, E>> transit(E event) {
		return transit(event, null);
	}

}
