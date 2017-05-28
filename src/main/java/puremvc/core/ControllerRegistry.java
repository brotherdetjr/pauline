package puremvc.core;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

import java.util.Map;

import static com.google.common.base.Optional.fromNullable;
import static com.google.common.collect.Maps.newHashMap;

public class ControllerRegistry<E extends Event> {

	private final Map<Anchor<? extends E, ?>, Controller<?, ?, E>> registry = newHashMap();

	public void put(Anchor<? extends E, ?> anchor, Controller<?, ?, E> controller) {
		registry.put(anchor, controller);
	}

	@SuppressWarnings({"unchecked", "SuspiciousMethodCalls"})
	public <From, To> Controller<From, To, E> get(E event, From state) {
		Class<? extends Event> eventClass = event.getClass();
		return (Controller<From, To, E>) fromNullable(getController(eventClass, state))
			.or(fromNullable(getController(eventClass, null)))
			.or(fromNullable(getController(Event.class, state)))
			.or(() -> {
				throw new IllegalArgumentException("No controller registered for state " + state +
					" and event class " + event.getClass().getName());
			});
	}

	@SuppressWarnings({"unchecked", "SuspiciousMethodCalls"})
	private <From, To> Controller<From, To, E> getController(Class<? extends Event> eventClass, From state) {
		while (Event.class.isAssignableFrom(eventClass)) {
			Controller<From, To, E> controller = ((Controller<From, To, E>)
				registry.get(Anchor.of(eventClass, state)));
			if (controller != null) {
				return controller;
			}
			eventClass = (Class<? extends Event>) eventClass.getSuperclass();
		}
		return null;
	}

	@RequiredArgsConstructor
	@EqualsAndHashCode
	public static class Anchor<E extends Event, State> {
		private final Class<E> eventClass;
		private final State state;

		public static <E extends Event, State> Anchor<E, State> of(Class<E> eventClass, State state) {
			return new Anchor<>(eventClass, state);
		}

		public static <E extends Event, State> Anchor<E, State> of(Class<E> eventClass) {
			return of(eventClass, null);
		}
	}

}
