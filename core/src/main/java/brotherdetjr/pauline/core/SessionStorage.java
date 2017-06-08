package brotherdetjr.pauline.core;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface SessionStorage {

	CompletableFuture<Void> acquireLock(long sessionId);

	<T> CompletableFuture<T> getState(long sessionId);

	CompletableFuture<Map<String, ?>> getVars(long sessionId);

	<T> CompletableFuture<Void> store(T state, Map<String, ?> vars);

	CompletableFuture<Void> releaseLock(long sessionId);
}
