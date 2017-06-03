package brotherdetjr.pauline.telegram;

import brotherdetjr.pauline.telegram.events.TelegramEvent;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;
import brotherdetjr.pauline.core.Engine;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static brotherdetjr.pauline.telegram.TelegramUtils.extractChatId;

@Slf4j
public class TelegramEngine {

	static {
		ApiContextInitializer.init();
	}

	@SneakyThrows(TelegramApiRequestException.class)
	public static Engine.Builder<TelegramRenderer, TelegramEvent> builder(String token, String name) {
		AtomicReference<Consumer<TelegramEvent>> ref = new AtomicReference<>();
		TelegramLongPollingBot bot = new TelegramBotImpl(token, name, ref);
		new TelegramBotsApi().registerBot(bot);
		return new Engine.Builder<TelegramRenderer, TelegramEvent>(ref::set)
			.rendererFactory(e -> new TelegramRenderer(bot, e.getChatId()))
			.failView(new TelegramDefaultFailView());
	}

}
