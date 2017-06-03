package brotherdetjr.pauline.telegram.events;

import brotherdetjr.pauline.events.Event;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class TelegramEvent implements Event {
	private final long sessionId;
	private final String userName;
	private final long userId;
	private final long chatId;
}
