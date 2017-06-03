package brotherdetjr.pauline.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public class TelegramBotImpl extends TelegramLongPollingBot {

	private final String token;
	private final String name;
	private final AtomicReference<Consumer<Update>> ref;

	@Override
	public String getBotToken() {
		return token;
	}

	@Override
	public void onUpdateReceived(Update event) {
		log.debug("Firing event: {}", event);
		ref.get().accept(event);
	}

	@Override
	public String getBotUsername() {
		return name;
	}
}
