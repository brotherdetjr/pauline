package puremvc.telegram;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiRequestException;
import puremvc.core.Mvc;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static puremvc.telegram.TelegramUtils.extractChatId;

@Slf4j
public class TelegramMvc {

	static {
		ApiContextInitializer.init();
	}

	@SneakyThrows(TelegramApiRequestException.class)
	public static Mvc.Builder<TelegramRenderer, Update> builder(String token, String name) {
		AtomicReference<Consumer<Update>> ref = new AtomicReference<>();
		TelegramLongPollingBot bot = new TelegramBotImpl(token, name, ref);
		new TelegramBotsApi().registerBot(bot);
		return new Mvc.Builder<TelegramRenderer, Update>(ref::set)
			.sessionIdFunc(TelegramUtils::extractUserId) // TODO something based both on userId and chatId
			.rendererFactory(e -> new TelegramRenderer(bot, extractChatId(e)))
			.failView(new TelegramDefaultFailView());
	}

}
