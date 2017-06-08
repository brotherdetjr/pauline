package brotherdetjr.pauline.core;

import brotherdetjr.pauline.events.Event;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class SpamException extends RuntimeException {

	private final long sessionId;
	private final List<Event> eventQueue;

}
