package puremvc.core;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;

public class ControllerRegistry<E extends Event> {

	private final Map<Anchor<? extends Event, ?>, Controller<?, ?, ? extends E>> registry = newHashMap();

	public <S> void put(Class<? extends E> eventClass, S state, Controller<?, ?, ? extends E> controller) {
		registry.put(Anchor.of(eventClass, state), controller);
	}

	public void put(Class<? extends E> eventClass, Controller<?, ?, ? extends E> controller) {
		registry.put(Anchor.of(eventClass), controller);
	}

	public <S> void put(Class<? extends E> eventClass, Class<S> stateClass, Controller<?, ?, ? extends E> controller) {
		registry.put(Anchor.of(eventClass, stateClass), controller);
	}

	public <From, To> Controller<From, To, E> get(Class<? extends Event> eventClass, From state) {
		Class<? extends Event> clazz = eventClass;
		while (clazz != null) {
			Controller<From, To, E> controller = getController(clazz, state);
			if (controller != null) {
				return controller;
			}
			controller = getController(clazz, state.getClass());
			if (controller != null) {
				return controller;
			}
			controller = getController(clazz);
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

	@SuppressWarnings("unchecked")
	private <From, To> Controller<From, To, E> getController(Class<? extends Event> eventClass, Class<?> stateClass) {
		while (stateClass != null) {
			Controller<?, ?, ? extends E> controller = registry.get(Anchor.of(eventClass, stateClass));
			if (controller != null) {
				return (Controller<From, To, E>) controller;
			}
			stateClass = stateClass.getSuperclass();
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private <From, To> Controller<From, To, E> getController(Class<? extends Event> eventClass) {
		return (Controller<From, To, E>) registry.get(Anchor.of(eventClass));
	}

	@RequiredArgsConstructor
	@EqualsAndHashCode
	@ToString
	private static class Anchor<E extends Event, State> {
		private final Class<E> eventClass;
		private final State state;
		private final Class stateClass;

		public static <E extends Event, State> Anchor<E, State> of(Class<E> eventClass, State state) {
			return new Anchor<>(eventClass, state, null);
		}

		public static <E extends Event, State> Anchor<E, State> of(Class<E> eventClass, Class<State> stateClass) {
			return new Anchor<>(eventClass, null, stateClass);
		}

		public static <E extends Event, State> Anchor<E, State> of(Class<E> eventClass) {
			return new Anchor<>(eventClass, null, null);
		}
	}

}
