package brotherdetjr.pauline.core;

import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface SessionStorage {

	CompletableFuture<Void> acquireLock(long sessionId);

	<T> CompletableFuture<Pair<T, Map<String, ?>>> getStateAndVars(long sessionId);

	<T> CompletableFuture<Void> store(T state, Map<String, ?> vars);

	CompletableFuture<Void> releaseLock(long sessionId);
}
