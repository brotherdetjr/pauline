package puremvc.core;

import java.util.function.Consumer;

public interface EventSource<E> {
	void onEvent(Consumer<E> handler);
}
