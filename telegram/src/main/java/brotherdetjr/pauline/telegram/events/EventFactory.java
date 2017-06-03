package brotherdetjr.pauline.telegram.events;

import lombok.experimental.UtilityClass;
import org.telegram.telegrambots.api.objects.Update;

@UtilityClass
public class EventFactory {
	public static TelegramEvent of(Update update) {
		if (update.hasMessage()) {
			long userId = Long.valueOf(update.getMessage().getFrom().getId());
			@SuppressWarnings("UnnecessaryLocalVariable") long sessionId = userId; // TODO use something that depends both on userId and chatId
			String userName = update.getMessage().getFrom().getUserName();
			long chatId = update.getMessage().getChatId();
			String text = update.getMessage().getText();
			if (text != null) {
				return new TextMessageEvent(sessionId, userName, userId, chatId, text);
			} else {
				return new TelegramEvent(sessionId, userName, userId, chatId);
			}
		} else {
			return null;
		}
	}
}
