package puremvc.telegram;

import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.api.objects.Update;
import puremvc.core.View;

import java.util.UUID;

import static com.google.common.base.Throwables.getStackTraceAsString;
import static java.util.UUID.randomUUID;
import static puremvc.telegram.TelegramUtils.extractChatId;
import static puremvc.telegram.TelegramUtils.extractText;
import static puremvc.telegram.TelegramUtils.extractUserId;

@Slf4j
public class TelegramDefaultFailView implements View<Throwable, TelegramRenderer, Update> {
	@Override
	public void render(Context<Throwable, TelegramRenderer, Update> ctx) {
		Update event = ctx.getEvent();
		UUID uuid = randomUUID();
		log.error("Error id: {}. User id: {}. Chat id: {}. Event text: {}. Stack trace: {}",
			uuid,
			extractUserId(event),
			extractChatId(event),
			extractText(event),
			getStackTraceAsString(ctx.getState())
		);
		ctx.getRenderer().send("Oops! Something went wrong. Please contact bot administrator. Error id: " + uuid);
	}
}
