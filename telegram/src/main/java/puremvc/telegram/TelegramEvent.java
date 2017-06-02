package puremvc.telegram;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.telegram.telegrambots.api.objects.Update;
import puremvc.core.Event;

import static puremvc.telegram.TelegramUtils.extractChatId;
import static puremvc.telegram.TelegramUtils.extractText;
import static puremvc.telegram.TelegramUtils.extractUserId;

@RequiredArgsConstructor
@Getter
@ToString
public class TelegramEvent implements Event {
	private final long sessionId;
	private final long chatId;
	private final String text;

	public static TelegramEvent of(Update update) {
		return new TelegramEvent(extractUserId(update), extractChatId(update), extractText(update));
	}
}
