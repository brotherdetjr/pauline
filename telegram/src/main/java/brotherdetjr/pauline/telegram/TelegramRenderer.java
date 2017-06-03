package brotherdetjr.pauline.telegram;

import java.util.concurrent.CompletableFuture;

public interface TelegramRenderer {
	CompletableFuture<?> send(String text);
}
