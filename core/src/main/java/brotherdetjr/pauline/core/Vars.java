package brotherdetjr.pauline.core;

import lombok.RequiredArgsConstructor;

import java.util.Map;

import static com.google.common.collect.Maps.newConcurrentMap;

/**
 * Key-value-map-alike object to manipulate with within a controller.
 * Represents a key-value-map for given sessionId.
 * Vars values can be modified concurrently.
 */
@RequiredArgsConstructor
public class Vars {
	private final SessionStorage storage;
	private final long sessionId;

	private final Map<String, ? super Object> vars = newConcurrentMap();

	/**
	 * Returns var value by key for given session.
	 * Returns overriding value, if it has been set for given key.
	 * Otherwise returns a value directly from storage.
	 * @param key specifies the key to retrieve value for.
	 * @param probe generic probe argument.
	 * @param <T> type of var value.
	 * @return value by key for given session.
	 */
	public <T> T getVar(String key, Class<T> probe) {
		return (T) vars.getOrDefault(key, storage.getVar(sessionId, key));
	}

	/**
	 * Overrides or creates a value for given key and session id.
	 * @param key specifies the key to set value for.
	 * @param value specifies the value to set.
	 * @param <T> value by key for given session.
	 */
	public <T> void setVar(String key, T value) {
		vars.put(key, value);
	}
}
