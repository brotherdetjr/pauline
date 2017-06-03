package brotherdetjr.pauline.telegram.events;

import brotherdetjr.pauline.core.Event;
import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;

@RequiredArgsConstructor
public class TelegramEvent implements Event {
	private final Update underlying;

	@Override
	public long getSessionId() {
		return getUserId(); // TODO something based both on userId and chatId
	}

	public Update getUnderlying() {
		return underlying;
	}

	public String getUserName() {
		return getMessage().getFrom().getUserName();
	}

	public long getUserId() {
		return getMessage().getFrom().getId();
	}

	public long getChatId() {
		return getMessage().getChatId();
	}

	protected Message getMessage() {
		return underlying.getMessage();
	}
}
