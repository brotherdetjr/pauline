package pauline.telegram;

import brotherdetjr.utils.ExceptionWithValue;
import lombok.experimental.UtilityClass;
import org.telegram.telegrambots.api.objects.Update;

@UtilityClass
public class TelegramUtils {

    public static String extractText(Update update) {
        if (update.hasMessage()) {
            return update.getMessage().getText();
        } else {
            throw new ExceptionWithValue(update);
        }
    }

    public static Long extractUserId(Update update) {
        if (update.hasMessage()) {
            return Long.valueOf(update.getMessage().getFrom().getId());
        } else {
            throw new ExceptionWithValue(update);
        }
    }

    public static Long extractChatId(Update update) {
        if (update.hasMessage()) {
            return update.getMessage().getChatId();
        } else {
            throw new ExceptionWithValue(update);
        }
    }
}
