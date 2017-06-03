package brotherdetjr.pauline.telegram;

import brotherdetjr.pauline.core.View;
import brotherdetjr.pauline.telegram.events.TelegramEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

import static com.google.common.base.Throwables.getStackTraceAsString;
import static java.util.UUID.randomUUID;

@Slf4j
public class TelegramDefaultFailView implements View<Throwable, TelegramRenderer, TelegramEvent> {
	@Override
	public void render(Context<Throwable, TelegramRenderer, TelegramEvent> ctx) {
		TelegramEvent event = ctx.getEvent();
		UUID uuid = randomUUID();
		log.error("Error id: {}. User name: {}. Chat id: {}. Stack trace: {}",
			uuid,
			event.getUserName(),
			event.getChatId(),
			getStackTraceAsString(ctx.getState())
		);
		ctx.getRenderer().send("Oops! Something went wrong. Please contact bot administrator. Error id: " + uuid);
	}
}
