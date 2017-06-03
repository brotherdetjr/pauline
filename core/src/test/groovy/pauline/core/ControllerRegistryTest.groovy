package pauline.core

import spock.lang.Specification
import spock.lang.Unroll

class ControllerRegistryTest extends Specification {

	static def c1 = { null } as Controller
	static def c2 = { null } as Controller

	static def state1 = 'state1'
	static def state2 = 'state2'

	@Unroll
	def 'subscription set 1, input: #eventClass.simpleName #state'() {
		given:
		def reg = new ControllerRegistry<E1>()
		reg.put E2_1, state1, c1
		expect:
		reg.get(eventClass, state) == expected
		where:
		eventClass | state  | expected
		E2_1       | state1 | c1
		E3         | state1 | c1
		E3         | state2 | null
		E2_2       | state2 | null
	}

	@Unroll
	def 'subscription set 2, input: #eventClass.simpleName #state'() {
		given:
		def reg = new ControllerRegistry<E1>()
		reg.put E1, state1, c1
		expect:
		reg.get(eventClass, state) == expected
		where:
		eventClass | state  | expected
		E2_1       | state1 | c1
		E3         | state1 | c1
		E3         | state2 | null
		E2_2       | state2 | null
		E2_2       | state1 | c1
	}

	@Unroll
	def 'subscription set 3, input: #eventClass.simpleName #state'() {
		given:
		def reg = new ControllerRegistry<Event>()
		reg.put E2_1, state1, c1
		reg.put E1, c2
		reg.put Event, String, c2
		reg.put Event, c1
		expect:
		reg.get(eventClass, state) == expected
		where:
		eventClass | state  | expected
		E2_1       | state1 | c1
		E3         | state1 | c1
		E3         | state2 | c2
		E2_2       | state2 | c2
		E2_2       | state1 | c2
		E1_2       | state1 | c2
		E1_2       | state2 | c2
		E1_2       | String | c1
		E1_2       | 123L   | c1
	}

	@Unroll
	def 'subscription set 4, input: #eventClass.simpleName #state'() {
		given:
		def reg = new ControllerRegistry<Event>()
		reg.put E2_1, S1, c1
		expect:
		reg.get(eventClass, state) == expected
		where:
		eventClass | state    | expected
		E2_1       | new S1() | c1
		E2_1       | new S2() | c1
		E2_1       | S1       | null
		E2_1       | S2       | null
		E2_1       | String   | null
		E2_2       | new S1() | null
		E2_2       | new S2() | null
		E2_2       | S1       | null
		E2_2       | S2       | null
		E2_2       | String   | null
	}

	class Event {}

	class E1 extends Event {
		long sessionId
	}

	class E1_2 extends Event {
		long sessionId
	}

	class E2_1 extends E1 {}

	class E2_2 extends E1 {}

	class E3 extends E2_1 {}

	class S1 {}

	class S2 extends S1 {}

}