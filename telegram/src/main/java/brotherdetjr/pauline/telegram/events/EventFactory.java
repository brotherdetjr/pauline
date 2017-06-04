package brotherdetjr.pauline.telegram.events;

import lombok.experimental.UtilityClass;
import org.telegram.telegrambots.api.objects.Update;

@UtilityClass
public class EventFactory {
	public static TelegramEvent of(Update update) {
		if (update.hasMessage()) {
			long userId = Long.valueOf(update.getMessage().getFrom().getId());
			String userName = update.getMessage().getFrom().getUserName();
			long chatId = update.getMessage().getChatId();
			String text = update.getMessage().getText();
			if (text != null) {
				return new TextMessageEvent(userName, userId, chatId, text);
			} else {
				return new TelegramEvent(userName, userId, chatId);
			}
		} else {
			return null;
		}
	}
}
