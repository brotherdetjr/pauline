package brotherdetjr.utils.test;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.io.Serializable;
import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

import static com.google.common.collect.Iterables.getFirst;
import static java.util.stream.Collectors.toSet;
import static org.apache.logging.log4j.core.layout.PatternLayout.createDefaultLayout;

@SuppressWarnings("unused")
@Plugin(name = "TestAppender", category = "Core", elementType = "appender", printObject = true)
public class TestAppender extends AbstractAppender {

	private Consumer<LogEvent> consumer;

	public TestAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions) {
		super(name, filter, layout, ignoreExceptions);
	}

	@Override
	public void append(LogEvent logEvent) {
		if (consumer != null) {
			consumer.accept(logEvent);
		}
	}

	@PluginFactory
	public static TestAppender createAppender(
		@PluginElement("Layout") Layout<? extends Serializable> layout,
		@PluginElement("Filter") final Filter filter) {
		return new TestAppender(
			TestAppender.class.getName(),
			filter,
			layout != null ? layout : createDefaultLayout(),
			true
		);
	}

	public static void setLogEventConsumer(Consumer<LogEvent> consumer) {
		getAppenders().forEach(a -> a.consumer = consumer);
	}

	public static Consumer<LogEvent> getLogEventConsumer() {
		Set<Consumer<LogEvent>> consumers = getAppenders().stream().map(a -> a.consumer).collect(toSet());
		assert consumers.size() == 1;
		return getFirst(consumers, null);
	}

	private static Collection<TestAppender> getAppenders() {
		return Log4J2Utils.getAppenders(TestAppender.class.getName());
	}
}
