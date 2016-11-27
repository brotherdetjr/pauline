package puremvc.core;

import com.google.common.base.Preconditions;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Utils {
	@SuppressWarnings("ResultOfMethodCallIgnored")
	public static void checkNotNull(Object ... objects) {
		for (Object object : objects) {
			Preconditions.checkNotNull(object);
		}
	}
}
