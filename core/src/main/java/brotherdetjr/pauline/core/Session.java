package brotherdetjr.pauline.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Map;

import static com.google.common.base.Optional.fromNullable;
import static com.google.common.collect.Maps.newHashMap;
import static lombok.AccessLevel.PRIVATE;

@RequiredArgsConstructor(access = PRIVATE)
public class Session<T> {
	@Getter
	private final long id;
	@Getter
	private final T state;
	private final Map<String, ?> vars;
	private final Map<String, ? super Object> newVars = newHashMap();

	public static <T> Session<T> of(long id, T state, Map<String, ?> vars) {
		return new Session<>(id, state, vars);
	}

	public <T> void setVar(String key, T value) {
		newVars.put(key, value);
	}

	public <T> T getVar(String key, Class<T> probe) {
		return (T) fromNullable(newVars.get(key)).or(vars.get(key));
	}
}
