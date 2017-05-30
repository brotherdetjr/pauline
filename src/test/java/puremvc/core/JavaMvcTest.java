package puremvc.core;

import com.google.common.collect.ImmutableList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.util.List;
import java.util.function.BiConsumer;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

public class JavaMvcTest {

	@Test
	public void toEnsureGenericsWorkCorrectly() {
		List<Pair<Long, Long>> rendered = newArrayList();
		EventSourceImpl<EventBase> eventSource = new EventSourceImpl<>();
		new Mvc.Builder<BiConsumer<Long, Long>, EventBase>(eventSource)
			.renderer((event, from) -> rendered.add(Pair.of(event, from)))
			.initial(event -> completedFuture(event.getValue()), Long.class)
			.handle(EventImplChild.class).when(555L).with(event -> completedFuture(event.getValue2()))
			.handle(EventImpl.class).when(101L).with(event -> completedFuture(event.getValue()))
			.handle(EventImpl.class).with((EventImpl event, Long from) -> completedFuture(from + event.getValue()))
			.handle().when(101L).with(event -> { throw new RuntimeException(); }) // must not be triggered
			.handle().with(event -> completedFuture(12L))
			.render(Long.class).as(ctx -> ctx.getRenderer().accept(ctx.getEvent().getSessionId(), ctx.getState()))
			.failView(ctx -> { throw new RuntimeException(); })
			.build();

		eventSource
			.fire(EventImpl.of(2, 32))
			.fire(EventImpl.of(2, 3))
			.fire(EventImpl.of(3, 99))
			.fire(EventImpl.of(2, 3))
			.fire(EventImpl.of(3, 2))
			.fire(EventImpl.of(3, 555))
			.fire(EventImplChild.of(3, 1024, 444))
			.fire(EventImplChild.of(3, 999, 111))
			.fire(EventImpl2.of(2, 20));

		assertThat(rendered, equalTo(
			ImmutableList.of(
				Pair.of(2L, 32L),
				Pair.of(2L, 35L),
				Pair.of(3L, 99L),
				Pair.of(2L, 38L),
				Pair.of(3L, 101L),
				Pair.of(3L, 555L),
				Pair.of(3L, 444L),
				Pair.of(3L, 1443L),
				Pair.of(2L, 12L)
			)
		));
	}

	@Getter
	@RequiredArgsConstructor
	@ToString
	public static abstract class EventBase implements Event {
		private final long sessionId;
		private final long value;
	}

	public static class EventImpl extends EventBase {
		public EventImpl(long sessionId, long value) {
			super(sessionId, value);
		}

		public static EventImpl of(long sessionId, long value) {
			return new EventImpl(sessionId, value);
		}
	}

	@Getter
	@ToString
	public static class EventImplChild extends EventImpl {
		private final long value2;

		public EventImplChild(long sessionId, long value, long value2) {
			super(sessionId, value);
			this.value2 = value2;
		}

		public static EventImplChild of(long sessionId, long value, long value2) {
			return new EventImplChild(sessionId, value, value2);
		}
	}

	public static class EventImpl2 extends EventBase {
		public EventImpl2(long sessionId, long value) {
			super(sessionId, value);
		}

		public static EventImpl2 of(long sessionId, long value) {
			return new EventImpl2(sessionId, value);
		}
	}

}
