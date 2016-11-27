package puremvc.core

import groovy.util.logging.Slf4j
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll
import spock.util.concurrent.BlockingVariable
import spock.util.concurrent.BlockingVariables

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.function.BiConsumer

import static com.google.common.util.concurrent.MoreExecutors.directExecutor
import static java.util.concurrent.CompletableFuture.completedFuture
import static java.util.concurrent.Executors.newFixedThreadPool

@Slf4j
@Timeout(2)
class MvcTest extends Specification {
	static final
		SESSION_1 = 2,
		SESSION_2 = 22,
		CHAT_1 = 3,
		CHAT_2 = 30,

		EXECUTORS = [
			fixed1: newFixedThreadPool(1),
			fixed5: newFixedThreadPool(5),
			fixed20: newFixedThreadPool(20),
			direct: directExecutor()
		]

	@Unroll
	def 'controller increments state and renders value with proper chatId. Executor: #executorName'() {
		given:
		def eventSource = new EventSourceImpl()
		def renderer = Mock(BiConsumer)
		def barriers = new BlockingVariables()
		new Mvc.Builder(eventSource)
			.executor(EXECUTORS[executorName])
			.failView({ throw new Exception() })
			.renderer(renderer)
			.initial({ completedFuture 29L })
			.controller(Long, { EventImpl e, long from -> completedFuture from + e.value })
			.view(
				Long,
				{ View.Context<Long, BiConsumer<String, Long>, EventImpl> ctx ->
					def text = ctx.event.sessionId + '->' + ctx.state
					ctx.renderer.accept text, ctx.event.chatId
					barriers.setProperty text, true
				}
			)
			.build()
		when:
		eventSource.fire EventImpl.of(SESSION_1, CHAT_1, 100500)
		barriers.getProperty '2->29'
		then:
		1 * renderer.accept('2->29', CHAT_1)
		when:
		eventSource.fire EventImpl.of(SESSION_1, CHAT_1, 4)
		barriers.getProperty '2->33'
		then:
		1 * renderer.accept('2->33', CHAT_1)
		when:
		eventSource.fire EventImpl.of(SESSION_2, CHAT_1, 9000)
		barriers.getProperty '22->29'
		then:
		1 * renderer.accept('22->29', CHAT_1)
		when:
		eventSource.fire EventImpl.of(SESSION_2, CHAT_1, 9)
		barriers.getProperty '22->38'
		then:
		1 * renderer.accept('22->38', CHAT_1)
		when:
		eventSource.fire EventImpl.of(SESSION_1, CHAT_1, 9)
		barriers.getProperty '2->42'
		then:
		1 * renderer.accept('2->42', CHAT_1)
		when:
		eventSource.fire EventImpl.of(SESSION_2, CHAT_2, 2)
		barriers.getProperty '22->40'
		then:
		1 * renderer.accept('22->40', CHAT_2)
		where:
		executorName << EXECUTORS.keySet()
	}

	@Unroll
	def '"not so fast" message is sent when previous request is not handled yet. Executor: #executorName'() {
		given:
		def barriers = new BlockingVariables()
		def serviceBarrier = new BlockingVariable<Boolean>()
		def service = new LongRunningService(newFixedThreadPool(1), serviceBarrier)
		def eventSource = new EventSourceImpl()
		def sender = Mock(BiConsumer)
		def renderer = { String text, long chatId ->
			log.debug "Sending '$text' to chat with id $chatId"
			sender.accept text, chatId
			if (text == '2->29') {
				barriers.setProperty 'zeroth', true
			} else if (text == 'not so fast') {
				barriers.setProperty 'first', true
			} else if (text == '2->31') {
				barriers.setProperty 'second', true
			}
			completedFuture null
		}
		new Mvc.Builder(eventSource)
			.executor(EXECUTORS[executorName])
			.failView(
				{ View.Context<Long, BiConsumer<String, Long>, EventImpl> ctx ->
					//noinspection GroovyAssignabilityCheck
					ctx.renderer 'not so fast', ctx.event.chatId
				}
			)
			.renderer(renderer)
			.initial({ completedFuture 29L })
			.controller(Long, { EventImpl e, long from -> service.sum from, e.value })
			.view(
				Long,
				{ View.Context<Long, BiConsumer<String, Long>, EventImpl> ctx ->
					def text = ctx.event.sessionId + '->' + ctx.state
					//noinspection GroovyAssignabilityCheck
					ctx.renderer text, ctx.event.chatId
					barriers.setProperty text, true
				}
			)
			.build()
		when:
		eventSource.fire EventImpl.of(SESSION_1, CHAT_1, 777)
		barriers.getProperty 'zeroth'
		then:
		1 * sender.accept('2->29', CHAT_1)
		when:
		eventSource.fire EventImpl.of(SESSION_1, CHAT_1, 2)
		eventSource.fire EventImpl.of(SESSION_1, CHAT_1, 3)
		barriers.getProperty 'first'
		then:
		1 * sender.accept('not so fast', CHAT_1)
		when:
		serviceBarrier.set true
		barriers.getProperty 'second'
		then:
		1 * sender.accept('2->31', CHAT_1)
		where:
		executorName << EXECUTORS.keySet()
	}

	static class EventImpl implements Event {
		long sessionId
		long chatId
		long value

		static EventImpl of(long sessionId, long chatId, long value) {
			new EventImpl(sessionId: sessionId, chatId: chatId, value: value)
		}

		@Override
		public String toString() {
			return "EventImpl{" +
				"sessionId=" + sessionId +
				", chatId=" + chatId +
				", value=" + value +
				'}';
		}
	}

	class LongRunningService {

		private final Executor executor
		private final BlockingVariable<Boolean> barrier

		LongRunningService(Executor executor, BlockingVariable<Boolean> barrier) {
			this.executor = executor
			this.barrier = barrier
		}

		CompletableFuture<Long> sum(long a, long b) {
			def future = new CompletableFuture<Integer>()
			executor.execute {
				barrier.get()
				future.complete a + b
			}
			future
		}
	}
}