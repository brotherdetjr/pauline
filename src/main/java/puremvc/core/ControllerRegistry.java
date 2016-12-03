package puremvc.core;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.function.BiPredicate;

import static com.google.common.collect.Maps.newHashMap;

public class ControllerRegistry<E extends Event> {

	private final Map<Anchor<? extends E, ?>, GuardedController<?, ?, ? extends E>> registry = newHashMap();

	public void put(Anchor<? extends E, ?> anchor, GuardedController<?, ?, ? extends E> guarded) {
		registry.put(anchor, guarded);
	}

	@SuppressWarnings({"unchecked"})
	public <From, To> Controller<From, To, E> get(E event, From state) {
		Class<? extends Event> eventClass = event.getClass();
		while (Event.class.isAssignableFrom(eventClass)) {
			Controller<From, To, E> controller = findByState(event, state, eventClass);
			if (controller != null) {
				return controller;
			}
			eventClass = (Class<? extends Event>) eventClass.getSuperclass();
		}
		return null;
	}

	@SuppressWarnings({"unchecked", "SuspiciousMethodCalls"})
	private <From, To> Controller<From, To, E> findByState(E event, From state, Class<? extends Event> eventClass) {
		Class<?> stateClass = state.getClass();
		while (stateClass != null) {
			GuardedController<From, ?, E> guarded =
				(GuardedController<From, ?, E>) registry.get(Anchor.of(eventClass, stateClass));
			if (guarded != null) {
				Controller<?, ?, ? extends E> controller = guarded.get(event, state);
				if (controller != null) {
					return (Controller<From, To, E>) controller;
				}
			}
			stateClass = stateClass.getSuperclass();
		}
		return null;
	}

	@RequiredArgsConstructor
	@EqualsAndHashCode()
	public static class Anchor<E extends Event, State> {
		private final Class<E> eventClass;
		private final Class<State> stateClass;

		public static <E extends Event, State> Anchor<E, State> of(Class<E> eventClass, Class<State> stateClass) {
			return new Anchor<>(eventClass, stateClass);
		}

	}

	@RequiredArgsConstructor
	public static class GuardedController<From, To, E extends Event> {
		private final BiPredicate<E, From> guard;
		private final Controller<From, To, E> controller;

		public Controller<From, To, E> get(E event, From state) {
			return guard.test(event, state) ? controller : null;
		}
	}
}
