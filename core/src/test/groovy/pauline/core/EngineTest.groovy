package pauline.core

import groovy.util.logging.Slf4j
import org.slf4j.Logger
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
class EngineTest extends Specification {
	static final
		SESSION_1 = 2L,
		SESSION_2 = 22L,
		CHAT_1 = 3L,
		CHAT_2 = 30L,

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
		new Engine.Builder(eventSource)
			.executor(EXECUTORS[executorName])
			.sessionIdFunc({ EventImpl e -> e.sessionId })
			.failView({ throw new Exception() })
			.rendererFactory({ renderer })
			.initial({ completedFuture 29L })
			.handle(EventImpl).by({ EventImpl e, long from -> completedFuture from + e.value })
			.render(Long).as({ View.Context<Long, BiConsumer<String, Long>, EventImpl> ctx ->
				def text = ctx.event.sessionId + '->' + ctx.state
				ctx.renderer.accept text, ctx.event.chatId
				barriers.setProperty text, true
			})
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
		def barriers = new BlockingVariables(2)
		def serviceBarrier = new BlockingVariable<Boolean>(2)
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
			} else if (text == '2->31' || text == '2->32') {
				barriers.setProperty 'second', true
			}
			completedFuture null
		}
		def mockedLog = Mock(Logger)
		new Engine.Builder(eventSource)
			.executor(EXECUTORS[executorName])
			.sessionIdFunc({ EventImpl e -> e.sessionId })
			.failView(
				{ View.Context<Long, BiConsumer<String, Long>, EventImpl> ctx ->
					//noinspection GroovyAssignabilityCheck
					ctx.renderer 'not so fast', ctx.event.chatId
				}
			)
			.rendererFactory({ renderer })
			.initial({ completedFuture 29L })
			.handle(EventImpl).by({ EventImpl e, long from -> service.sum from, e.value })
			.render(Long).as({ View.Context<Long, BiConsumer<String, Long>, EventImpl> ctx ->
				def text = ctx.event.sessionId + '->' + ctx.state
				//noinspection GroovyAssignabilityCheck
				ctx.renderer text, ctx.event.chatId
				barriers.setProperty text, true
			})
			.log(mockedLog)
			.build()
		when:
		eventSource.fire EventImpl.of(SESSION_1, CHAT_1, 777)
		barriers.getProperty 'zeroth'
		then:
		1 * sender.accept('2->29', CHAT_1)
		when:
		eventSource.fire EventImpl.of(SESSION_1, CHAT_1, 3)
		eventSource.fire EventImpl.of(SESSION_1, CHAT_1, 2)
		barriers.getProperty 'first'
		then:
		1 * mockedLog.error('Looks like somebody spamming us. Event: {}', _ as EventImpl)
		1 * sender.accept('not so fast', CHAT_1)
		when:
		serviceBarrier.set true
		barriers.getProperty 'second'
		then:
		1 * sender.accept({ it in ['2->31', '2->32']} as String, CHAT_1)
		where:
		executorName << EXECUTORS.keySet()
	}

	def 'event is logged before running in executor'() {
		given:
		def eventSource = new EventSourceImpl()
		def mockedLog = Mock(Logger)
		new Engine.Builder(eventSource)
			.sessionIdFunc({ EventImpl e -> e.sessionId })
			.failView({ -> })
			.rendererFactory({ EventImpl e -> { -> } })
			.initial({ -> })
			.handle(EventImpl).when(Object).by({ -> }) // .when(Object) here to do more coverage
			.log(mockedLog)
			.build()
		when:
		eventSource.fire EventImpl.of(SESSION_1, CHAT_1, 1313)
		then:
		1 * mockedLog.debug('Received event {}', _ as EventImpl)
	}

	def 'executor exception is logged'() {
		given:
		def eventSource = new EventSourceImpl()
		def mockedLog = Mock(Logger)
		new Engine.Builder(eventSource)
			.executor({ throw new Exception() })
			.sessionIdFunc({ EventImpl e -> e.sessionId })
			.failView({ -> })
			.rendererFactory({ EventImpl e -> { -> } })
			.initial({ -> })
			.handle(EventImpl).by({ -> })
			.log(mockedLog)
			.build()
		when:
		eventSource.fire EventImpl.of(SESSION_1, CHAT_1, 1313)
		then:
		1 * mockedLog.error('Failed to execute event handling. Event: {}. Cause: {}', _ as EventImpl, _ as String)
	}

	def 'session registration is logged once'() {
		given:
		def eventSource = new EventSourceImpl()
		def mockedLog = Mock(Logger)
		new Engine.Builder(eventSource)
			.sessionIdFunc({ EventImpl e -> e.sessionId })
			.failView({ -> })
			.rendererFactory({ EventImpl e -> { -> } })
			.initial({ -> })
			.handle(EventImpl).by({ -> })
			.log(mockedLog)
			.build()
		when:
		eventSource.fire EventImpl.of(SESSION_1, CHAT_1, 1313)
		then:
		1 * mockedLog.debug('Registering session {}', SESSION_1)
		when:
		eventSource.fire EventImpl.of(SESSION_1, CHAT_1, 1313)
		then:
		0 * mockedLog.debug('Registering session {}', SESSION_1)
	}

	def 'event processing exception is logged and rendered by failView'() {
		given:
		def eventSource = new EventSourceImpl()
		def mockedLog = Mock(Logger)
		def failView = Mock(View)
		new Engine.Builder(eventSource)
			.sessionIdFunc({ EventImpl e -> e.sessionId })
			.failView(failView)
			.rendererFactory({ EventImpl e -> { -> } })
			.initial({ -> })
			.handle(EventImpl).by({ -> })
			.log(mockedLog)
			.build()
		when:
		eventSource.fire Mock(EventImpl) {
			//noinspection GroovyAssignabilityCheck
			getSessionId() >> { throw new Exception() }
		}
		then:
		1 * mockedLog.error('Failed to process event {}: {}', _ as EventImpl, _ as String)
		1 * failView.render(_ as View.Context)
	}

	def 'failView exception is logged'() {
		given:
		def eventSource = new EventSourceImpl()
		def mockedLog = Mock(Logger)
		new Engine.Builder(eventSource)
			.sessionIdFunc({ EventImpl e -> e.sessionId })
			.failView(Mock(View) {
				render(_ as View.Context) >> { throw new Exception() }
			})
			.rendererFactory({ EventImpl e -> { -> } })
			.initial({ -> })
			.log(mockedLog)
			.build()
		when:
		eventSource.fire Mock(EventImpl) {
			//noinspection GroovyAssignabilityCheck
			getSessionId() >> { throw new Exception() }
		}
		then:
		1 * mockedLog.error('Failed to process event {}: {}', _ as EventImpl, _ as String)
		1 * mockedLog.error('Failed to process event {} and to render it: {}', _ as EventImpl, _ as String)
	}

	@SuppressWarnings("GroovyAssignabilityCheck")
	def 'No defined view for state exception is logged'() {
		given:
		def failed = false
		def event = EventImpl.of(SESSION_1, CHAT_1, 1313)
		def eventSource = new EventSourceImpl()
		def nextState = 2L
		def mockedLog = Mock(Logger) {
			1 * error('Failed to perform transition by event {}. Cause: {}', _ as EventImpl, _ as String) >>
				{ String msg, EventImpl evt, String trace ->
					failed |= !event.is(evt)
					failed |= !trace.contains("No view defined for state class ${nextState.class.name}")
				}
		}
		new Engine.Builder(eventSource)
			.sessionIdFunc({ EventImpl e -> e.sessionId })
			.initial({ completedFuture nextState })
			.rendererFactory({ EventImpl e -> { -> } })
			.failView({ -> })
			.log(mockedLog)
			.build()
		when:
		eventSource.fire event
		then:
		!failed
	}

	static class EventImpl {
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