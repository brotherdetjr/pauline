package brotherdetjr.pauline.telegram;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.api.methods.BotApiMethod;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.updateshandlers.SentCallback;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
public class TelegramRendererImpl implements TelegramRenderer {
	private final TelegramLongPollingBot bot;
	private final long chatId;

	@SneakyThrows(TelegramApiException.class)
	public CompletableFuture<?> send(String text) {
		CompletableFuture<Message> future = new CompletableFuture<>();
		SendMessage message = new SendMessage();
		message.setText(text);
		message.setChatId(Long.toString(chatId));
		log.debug("Sending '{}' to chat with id {}", text, chatId);
		bot.sendMessageAsync(message, new SentCallback<Message>() {
			@Override
			public void onResult(BotApiMethod<Message> botApiMethod, Message message) {
				future.complete(message);
			}

			@Override
			public void onError(BotApiMethod<Message> botApiMethod, TelegramApiRequestException e) {
				future.completeExceptionally(e);
			}

			@Override
			public void onException(BotApiMethod<Message> method, Exception exception) {
				future.completeExceptionally(exception);
			}
		});
		return future;
	}
}
