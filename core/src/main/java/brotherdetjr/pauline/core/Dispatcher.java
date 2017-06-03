package brotherdetjr.pauline.core;

@FunctionalInterface
public interface Dispatcher<E extends Event> {

	<From, To> Controller<From, To, E> dispatch(E event, From state);

	default <From, To> Controller<From, To, E> dispatch(E event) {
		return dispatch(event, null);
	}
}
