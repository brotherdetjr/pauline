package puremvc.core

import spock.lang.Specification

import static puremvc.core.ControllerRegistry.Anchor.of as anchor


class ControllerRegistryTest extends Specification {
	def 'does my day'() {
		given:
		def reg = new ControllerRegistry()
//		reg.put(anchor())
		expect:
		2 + 3 == 5
	}

	class E implements Event {
		long sessionId
	}

	class ChildAOfE extends E {}

	class ChildBOfE extends E {}

	class ChildAOfChildAOfE extends ChildAOfE {}

	class S {}

	class ChildAOfS extends S {}

	class ChildBOfS extends S {}

	class ChildAOfChildBOfS extends ChildBOfS {}
}