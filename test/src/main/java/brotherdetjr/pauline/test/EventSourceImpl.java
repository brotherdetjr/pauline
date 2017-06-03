package brotherdetjr.pauline.test;

import brotherdetjr.pauline.events.Event;
import brotherdetjr.pauline.events.EventSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class EventSourceImpl<E extends Event> implements EventSource<E> {

	private final List<Consumer<E>> handlers = new ArrayList<>();

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
