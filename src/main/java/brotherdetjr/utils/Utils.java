package brotherdetjr.utils;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import lombok.experimental.UtilityClass;
import puremvc.core.Controller;
import puremvc.core.ControllerRegistry;

import java.util.function.Function;

@UtilityClass
public class Utils {
	@SuppressWarnings("ResultOfMethodCallIgnored")
	public static void checkNotNull(Object ... objects) {
		for (Object object : objects) {
			Preconditions.checkNotNull(object);
		}
	}

	public static void propagateIfError(Throwable e) {
		if (e instanceof Error) {
			throw (Error) e;
		}
	}

	public static <V> V searchInHierarchy(Class<?> clazz, Function<Class<?>, V> func) {
		while (clazz != null) {
			V value = func.apply(clazz);
			if (value != null) {
				return value;
			}
			clazz = clazz.getSuperclass();
		}
		return null;
	}
}
