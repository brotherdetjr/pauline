package brotherdetjr.pauline.core;

import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;

import static com.google.common.base.Optional.fromNullable;
import static com.google.common.collect.Maps.newConcurrentMap;
import static lombok.AccessLevel.PRIVATE;

@RequiredArgsConstructor(access = PRIVATE)
public class Session<T> {
	@Getter
	private final long id;
	@Getter
	private final T state;
	private final Map<String, ?> vars;
	private final Map<String, ? super Object> newVars = newConcurrentMap();

	public static <T> Session<T> of(long id, Pair<T, Map<String, ?>> stateAndVars) {
		return new Session<>(id, stateAndVars.getLeft(), stateAndVars.getRight());
	}

	public <V> void setVar(String key, V value) {
		newVars.put(key, value);
	}

	public <V> V getVar(String key, Class<V> probe) {
		return (V) fromNullable(newVars.get(key)).or(vars.get(key));
	}

	public Map<String, ?> getVars() {
		return ImmutableMap.copyOf(newVars);
	}
}
