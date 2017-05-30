package puremvc.core;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;

public class ControllerRegistry<E extends Event> {

	private final Map<Anchor<? extends Event, ?>, Controller<?, ?, ? extends E>> registry = newHashMap();

	public void put(Anchor<? extends E, ?> anchor, Controller<?, ?, ? extends E> controller) {
		registry.put(anchor, controller);
	}

	public <From, To> Controller<From, To, E> get(Class<? extends Event> eventClass, From state) {
		Class<? extends Event> clazz = eventClass;
		while (clazz != null) {
			Controller<From, To, E> controller = getController(clazz, state);
			if (controller != null) {
				return controller;
			}
			controller = getController(clazz, null);
			if (controller != null) {
				return controller;
			}
			clazz = getParent(clazz);
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static Class<? extends Event> getParent(Class<? extends Event> eventClass) {
		eventClass = (Class<? extends Event>) eventClass.getSuperclass();
		return Object.class.equals(eventClass) ? Event.class : eventClass;
	}

	@SuppressWarnings("unchecked")
	private <From, To> Controller<From, To, E> getController(Class<? extends Event> eventClass, From state) {
		return (Controller<From, To, E>) registry.get(Anchor.of(eventClass, state));
	}

	@RequiredArgsConstructor
	@EqualsAndHashCode
	@ToString
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
