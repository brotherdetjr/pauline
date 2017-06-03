package pauline.telegram;

import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.api.objects.Update;
import pauline.core.View;

import java.util.UUID;

import static com.google.common.base.Throwables.getStackTraceAsString;
import static java.util.UUID.randomUUID;

@Slf4j
public class TelegramDefaultFailView implements View<Throwable, TelegramRenderer, Update> {
	@Override
	public void render(Context<Throwable, TelegramRenderer, Update> ctx) {
		Update event = ctx.getEvent();
		UUID uuid = randomUUID();
		log.error("Error id: {}. User id: {}. Chat id: {}. Event text: {}. Stack trace: {}",
			uuid,
			TelegramUtils.extractUserId(event),
			TelegramUtils.extractChatId(event),
			TelegramUtils.extractText(event),
			getStackTraceAsString(ctx.getState())
		);
		ctx.getRenderer().send("Oops! Something went wrong. Please contact bot administrator. Error id: " + uuid);
	}
}
