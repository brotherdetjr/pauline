package puremvc.core;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.Map;

import static brotherdetjr.utils.Utils.searchInHierarchy;
import static com.google.common.collect.Maps.newHashMap;

public class ControllerRegistry<E> {

	private final Map<Anchor<? extends E, ?>, Controller<?, ?, ? extends E>> registry = newHashMap();

	public <From, To, E1 extends E> void put(Class<E1> eventClass, From state, Controller<From, To, E1> controller) {
		registry.put(Anchor.of(eventClass, state), controller);
	}

	public <From, To, E1 extends E> void put(Class<E1> eventClass, Controller<From, To, E1> controller) {
		registry.put(Anchor.of(eventClass), controller);
	}

	public <From, To, E1 extends E> void put(
		Class<E1> eventClass, Class<From> stateClass, Controller<From, To, E1> controller) {
		registry.put(Anchor.of(eventClass, stateClass), controller);
	}

	@SuppressWarnings("unchecked")
	public <From, To, E1 extends E> Controller<From, To, E1> get(Class<E1> eventClass, From state) {
		Class<E1> clazz = eventClass;
		while (clazz != null) {
			Controller<From, To, E1> controller = getController(clazz, state);
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
			clazz = (Class<E1>) getParent(clazz);
		}
		return null;
	}

	private static <E> Class<? super E> getParent(Class<E> eventClass) {
		return eventClass.getSuperclass();
	}

	@SuppressWarnings("unchecked")
	private <From, To, E1 extends E> Controller<From, To, E1> getController(Class<E1> eventClass, From state) {
		return (Controller<From, To, E1>) registry.get(Anchor.of(eventClass, state));
	}

	@SuppressWarnings("unchecked")
	private <From, To, E1 extends E> Controller<From, To, E1> getController(Class<E1> eventClass, Class<?> stateClass) {
		return searchInHierarchy(stateClass, c -> (Controller<From, To, E1>) registry.get(Anchor.of(eventClass, c)));
	}

	@SuppressWarnings("unchecked")
	private <From, To, E1 extends E> Controller<From, To, E1> getController(Class<E1> eventClass) {
		return (Controller<From, To, E1>) registry.get(Anchor.of(eventClass));
	}

	@RequiredArgsConstructor
	@EqualsAndHashCode
	@ToString
	private static class Anchor<E, State> {
		private final Class<E> eventClass;
		private final State state;
		private final Class stateClass;

		public static <E, State> Anchor<E, State> of(Class<E> eventClass, State state) {
			return new Anchor<>(eventClass, state, null);
		}

		public static <E, State> Anchor<E, State> of(Class<E> eventClass, Class<State> stateClass) {
			return new Anchor<>(eventClass, null, stateClass);
		}

		public static <E, State> Anchor<E, State> of(Class<E> eventClass) {
			return new Anchor<>(eventClass, null, null);
		}
	}

}
