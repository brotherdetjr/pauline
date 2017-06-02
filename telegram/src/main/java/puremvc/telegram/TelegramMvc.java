package puremvc.telegram;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;
import puremvc.core.Mvc;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Slf4j
public class TelegramMvc {

	@SneakyThrows(TelegramApiRequestException.class)
	public static Mvc.Builder<TelegramRenderer, TelegramEvent> builder(String token, String name) {
		AtomicReference<Consumer<TelegramEvent>> ref = new AtomicReference<>();
		TelegramLongPollingBot bot = new TelegramBotImpl(token, name, ref);
		new TelegramBotsApi().registerBot(bot);
		return new Mvc.Builder<TelegramRenderer, TelegramEvent>(ref::set)
			.rendererFactory(e -> new TelegramRenderer(bot, e.getChatId()))
			.failView(new TelegramDefaultFailView());
	}

}
