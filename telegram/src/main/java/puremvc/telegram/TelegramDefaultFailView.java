package puremvc.telegram;

import lombok.extern.slf4j.Slf4j;
import puremvc.core.View;

import java.util.UUID;

import static com.google.common.base.Throwables.getStackTraceAsString;
import static java.util.UUID.randomUUID;

@Slf4j
public class TelegramDefaultFailView implements View<Throwable, TelegramRenderer, TelegramEvent> {
	@Override
	public void render(Context<Throwable, TelegramRenderer, TelegramEvent> ctx) {
		TelegramEvent event = ctx.getEvent();
		UUID uuid = randomUUID();
		log.error("Error id: {}. User id: {}. Chat id: {}. Event text: {}. Stack trace: {}",
			uuid,
			event.getSessionId(),
			event.getChatId(),
			event.getText(),
			getStackTraceAsString(ctx.getState())
		);
		ctx.getRenderer().send("Oops! Something went wrong. Please contact bot administrator. Error id: " + uuid);
	}
}
