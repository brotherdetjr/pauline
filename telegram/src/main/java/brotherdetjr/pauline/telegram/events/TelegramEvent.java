package brotherdetjr.pauline.telegram.events;

import brotherdetjr.pauline.events.Event;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class TelegramEvent implements Event {
	private final String userName;
	private final long userId;
	private final long chatId;

	@Override
	public long getSessionId() {
		return userId; // TODO use something that depends both on userId and chatId
	}
}
