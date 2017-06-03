package brotherdetjr.pauline.telegram.events;

import lombok.experimental.UtilityClass;
import org.telegram.telegrambots.api.objects.Update;

@UtilityClass
public class EventFactory {
	public static TelegramEvent of(Update update) {
		if (update.hasMessage()) {
			if (update.getMessage().getText() != null) {
				return new TextMessageEvent(update);
			} else {
				return new TelegramEvent(update);
			}
		} else {
			return null;
		}
	}
}
