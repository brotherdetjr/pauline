package puremvc.core;

@FunctionalInterface
public interface Dispatcher<E extends Event> {

	<From> Controller<From, ?, E> dispatch(E event, From state);

	default <From> Controller<From, ?, E> dispatch(E event) {
		return dispatch(event, null);
	}
}
