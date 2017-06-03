package pauline.core;

@FunctionalInterface
public interface Dispatcher<E> {

	<From, To> Controller<From, To, E> dispatch(E event, From state);

	default <From, To> Controller<From, To, E> dispatch(E event) {
		return dispatch(event, null);
	}
}
