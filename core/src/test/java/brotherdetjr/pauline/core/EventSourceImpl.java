package brotherdetjr.pauline.core;

import java.util.List;
import java.util.function.Consumer;

import static com.google.common.collect.Lists.newArrayList;

public class EventSourceImpl<E extends Event> implements EventSource<E> {

	private final List<Consumer<E>> handlers = newArrayList();

	@Override
	public void onEvent(Consumer<E> handler) {
		handlers.add(handler);
	}

	public EventSourceImpl<E> fire(E event) {
		for (Consumer<E> handler : handlers) {
			handler.accept(event);
		}
		return this;
	}
}
