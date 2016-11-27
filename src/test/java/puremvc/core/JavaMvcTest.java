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
		EventSourceImpl<EventImpl> eventSource = new EventSourceImpl<>();
		new Mvc.Builder<BiConsumer<Long, Long>, EventImpl>(eventSource)
			.renderer((event, from) -> rendered.add(Pair.of(event, from)))
			.initial(event -> completedFuture(event.getValue()), Long.class)
			.controller(Long.class, (event, from) -> completedFuture(from + event.getValue()))
			.view(Long.class, ctx -> ctx.getRenderer().accept(ctx.getEvent().getSessionId(), ctx.getState()))
			.failView(ctx -> { throw new RuntimeException(); })
			.build();

		eventSource
			.fire(EventImpl.of(2, 32))
			.fire(EventImpl.of(2, 3))
			.fire(EventImpl.of(3, 99))
			.fire(EventImpl.of(2, 3))
			.fire(EventImpl.of(3, 2));

		assertThat(rendered, equalTo(
			ImmutableList.of(
				Pair.of(2L, 32L),
				Pair.of(2L, 35L),
				Pair.of(3L, 99L),
				Pair.of(2L, 38L),
				Pair.of(3L, 101L)
			)
		));
	}

	@Getter
	@RequiredArgsConstructor
	@ToString
	public static class EventImpl implements Event {
		private final long sessionId;
		private final long value;

		public static EventImpl of(long sessionId, long value) {
			return new EventImpl(sessionId, value);
		}
	}

}
