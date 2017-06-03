package brotherdetjr.pauline.telegram;

import brotherdetjr.pauline.core.Flow;
import brotherdetjr.pauline.telegram.events.TelegramEvent;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@UtilityClass
@Slf4j
public class TelegramFlowConfigurer {

	private final static TelegramBotsApi telegramBotsApi = new TelegramBotsApi();

	static {
		ApiContextInitializer.init();
	}

	public static Flow.Builder<TelegramRenderer, TelegramEvent> flow() {
		return new Flow.Builder<TelegramRenderer, TelegramEvent>().failView(new TelegramDefaultFailView());
	}

	@SneakyThrows(TelegramApiRequestException.class)
	public static Flow.Builder<TelegramRenderer, TelegramEvent> configure(
		Flow.Builder<TelegramRenderer, TelegramEvent> builder, String token, String name) {
		AtomicReference<Consumer<TelegramEvent>> ref = new AtomicReference<>();
		TelegramLongPollingBot bot = new TelegramBotImpl(token, name, ref);
		telegramBotsApi.registerBot(bot);
		return builder
			.eventSource(ref::set)
			.rendererFactory(e -> new TelegramRendererImpl(bot, e.getChatId()));
	}

}
