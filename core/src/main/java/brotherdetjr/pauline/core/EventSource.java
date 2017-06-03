package brotherdetjr.pauline.core;

import java.util.function.Consumer;

public interface EventSource<E extends Event> {
	void onEvent(Consumer<E> handler);
}
