package brotherdetjr.pauline.telegram;

import brotherdetjr.pauline.telegram.events.EventFactory;
import brotherdetjr.pauline.telegram.events.TelegramEvent;
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
	private final AtomicReference<Consumer<TelegramEvent>> ref;

	@Override
	public String getBotToken() {
		return token;
	}

	@Override
	public void onUpdateReceived(Update update) {
		log.debug("Received update: {}", update);
		TelegramEvent event = EventFactory.of(update);
		if (event != null) {
			Consumer<TelegramEvent> consumer = ref.get();
			if (consumer != null) {
				log.debug("Firing event: {}", event);
				consumer.accept(event);
			} else {
				log.warn("No consumer yet");
			}
		} else {
			log.debug("Could not translate update to event");
		}
	}

	@Override
	public String getBotUsername() {
		return name;
	}
}
