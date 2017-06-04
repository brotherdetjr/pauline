package brotherdetjr.pauline.telegram.test;

import brotherdetjr.pauline.telegram.events.TextMessageEvent;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TelegramEvents {
	public static TextMessageEvent textMessage(String text) {
		return new TextMessageEvent("testUser", 33L, 9000L, text);
	}
}
