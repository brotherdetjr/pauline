package brotherdetjr.pauline.telegram.events;

import lombok.Getter;

@Getter
public class TextMessageEvent extends TelegramEvent {
	private final String text;

	public TextMessageEvent(long sessionId, String userName, long userId, long chatId, String text) {
		super(sessionId, userName, userId, chatId);
		this.text = text;
	}
}
