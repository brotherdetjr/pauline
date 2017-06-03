package brotherdetjr.pauline.telegram.events;

import org.telegram.telegrambots.api.objects.Update;

public class TextMessageEvent extends TelegramEvent {

	public TextMessageEvent(Update underlying) {
		super(underlying);
	}

	public String getText() {
		return getMessage().getText();
	}
}
