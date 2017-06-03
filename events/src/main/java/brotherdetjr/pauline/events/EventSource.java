package brotherdetjr.pauline.events;

import java.util.function.Consumer;

@FunctionalInterface
public interface EventSource<E extends Event> {
	void onEvent(Consumer<E> handler);
}
