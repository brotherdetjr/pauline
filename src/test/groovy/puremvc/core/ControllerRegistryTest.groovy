package puremvc.core

import spock.lang.Specification
import spock.lang.Unroll

import static brotherdetjr.utils.Utils.biFalse
import static puremvc.core.ControllerRegistry.Anchor.of as anchor
import static puremvc.core.ControllerRegistry.Guarded.of as guarded

class ControllerRegistryTest extends Specification {

	static def c1 = { null } as Controller
	static def c2 = { null } as Controller

	@Unroll
	def 'subscription set 1, input: #event.class.simpleName #state.class.simpleName'() {
		given:
		def reg = new ControllerRegistry()
		reg.put anchor(E3, Object), guarded(c1)
		expect:
		reg.get(event, state) == expected
		where:
		event      | state      | expected
		new E3()   | new S1()   | c1
		new E3()   | new S3()   | c1
		new E2_1() | new S1()   | null
		new E2_2() | new S2_2() | null
	}

	@Unroll
	def 'subscription set 2, input: #event.class.simpleName #state.class.simpleName'() {
		given:
		def reg = new ControllerRegistry()
		reg.put anchor(E1, S2_2), guarded(c1)
		expect:
		reg.get(event, state) == expected
		where:
		event    | state      | expected
		new E3() | new S3()   | c1
		new E1() | new S2_2() | c1
		new E1() | new S2_1() | null
	}

	@Unroll
	def 'subscription set 2a (guard rejects), input: #event.class.simpleName #state.class.simpleName'() {
		given:
		def reg = new ControllerRegistry()
		reg.put anchor(E1, S2_2), guarded(c1, biFalse())
		expect:
		reg.get(event, state) == expected
		where:
		event    | state      | expected
		new E3() | new S3()   | null
		new E1() | new S2_2() | null
		new E1() | new S2_1() | null
	}

	@Unroll
	def 'subscription set 3, input: #event.class.simpleName #state.class.simpleName'() {
		given:
		def reg = new ControllerRegistry()
		reg.put anchor(E1, S3), guarded(c1)
		reg.put anchor(E2_1, S2_2), guarded(c2)
		expect:
		reg.get(event, state) == expected
		where:
		event      | state    | expected
		new E2_2() | new S3() | c1
		new E3()   | new S3() | c2
		new E3()   | new S1() | null
	}

	class E1 implements Event {
		long sessionId
	}

	class E2_1 extends E1 {}

	class E2_2 extends E1 {}

	class E3 extends E2_1 {}

	class S1 {}

	class S2_1 extends S1 {}

	class S2_2 extends S1 {}

	class S3 extends S2_2 {}
}