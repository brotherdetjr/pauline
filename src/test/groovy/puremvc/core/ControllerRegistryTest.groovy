package puremvc.core

import spock.lang.Specification
import spock.lang.Unroll

class ControllerRegistryTest extends Specification {

	static def c1 = { null } as Controller
	static def c2 = { null } as Controller

	static def state1 = 'state1'
	static def state2 = 'state2'

	@Unroll
	def 'subscription set 1, input: #event.class.simpleName #state'() {
		given:
		def reg = new ControllerRegistry<E1>()
		reg.put E2_1, state1, c1
		expect:
		reg.get(event.class, state) == expected
		where:
		event      | state  | expected
		new E2_1() | state1 | c1
		new E3()   | state1 | c1
		new E3()   | state2 | null
		new E2_2() | state2 | null
	}

	@Unroll
	def 'subscription set 2, input: #event.class.simpleName #state'() {
		given:
		def reg = new ControllerRegistry<E1>()
		reg.put E1, state1, c1
		expect:
		reg.get(event.class, state) == expected
		where:
		event      | state  | expected
		new E2_1() | state1 | c1
		new E3()   | state1 | c1
		new E3()   | state2 | null
		new E2_2() | state2 | null
		new E2_2() | state1 | c1
	}

	@Unroll
	def 'subscription set 3, input: #event.class.simpleName #state'() {
		given:
		def reg = new ControllerRegistry<Event>()
		reg.put E2_1, state1, c1
		reg.put E1, null, c2
		reg.put Event, String, c2
		reg.put Event, null, c1
		expect:
		reg.get(event.class, state) == expected
		where:
		event      | state  | expected
		new E2_1() | state1 | c1
		new E3()   | state1 | c1
		new E3()   | state2 | c2
		new E2_2() | state2 | c2
		new E2_2() | state1 | c2
		new E1_2() | state1 | c2
		new E1_2() | state2 | c2
		new E1_2() | String | c2
		new E1_2() | 123L   | c1
	}

	class E1 implements Event {
		long sessionId
	}

	class E1_2 implements Event {
		long sessionId
	}

	class E2_1 extends E1 {}

	class E2_2 extends E1 {}

	class E3 extends E2_1 {}

}