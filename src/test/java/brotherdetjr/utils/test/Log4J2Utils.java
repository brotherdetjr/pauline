package brotherdetjr.utils.test;

import lombok.experimental.UtilityClass;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.selector.ClassLoaderContextSelector;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.stream.Collectors.toList;

@UtilityClass
public class Log4J2Utils {

	@SuppressWarnings("unchecked")
	public static <T extends Appender> Collection<T> getAppenders(String name) {
		try {
			Field contextMapField = ClassLoaderContextSelector.class.getDeclaredField("CONTEXT_MAP");
			contextMapField.setAccessible(true);
			Map<String, AtomicReference<WeakReference<LoggerContext>>> contextMap =
				(Map<String, AtomicReference<WeakReference<LoggerContext>>>) contextMapField.get(null);
			return contextMap.values().stream()
				.map(AtomicReference::get)
				.filter(e -> e != null)
				.map(Reference::get)
				.filter(e -> e != null)
				.map(e -> (T) e.getConfiguration().getAppender(name))
				.filter(e -> e != null)
				.collect(toList());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
