package brotherdetjr.utils

import spock.lang.Specification
import spock.lang.Unroll

import static brotherdetjr.utils.Utils.checkNotNull
import static brotherdetjr.utils.Utils.propagateIfError
import static brotherdetjr.utils.Utils.searchInHierarchy


class UtilsTest extends Specification {

	def 'checkNotNull() throws NullPointerException if any of the args is null'() {
		when:
		checkNotNull 1, 2, 3
		then:
		notThrown NullPointerException
		when:
		checkNotNull null
		then:
		thrown NullPointerException
		when:
		checkNotNull 1, 2, null
		then:
		thrown NullPointerException
	}

	def 'propagateIfError() propagates given throwable if it is of Error type'() {
		when:
		propagateIfError new Exception()
		then:
		notThrown Throwable
		when:
		propagateIfError new OutOfMemoryError()
		then:
		thrown OutOfMemoryError
	}

	@Unroll
	def 'searchInHierarchy() returns value of func if func(clazz) != null otherwise it goes up the hierarchy'() {
		expect:
		searchInHierarchy(clazz, { it == S2 ? 'a value' : null }) == expected
		where:
		clazz  | expected
		S1     | null
		S2     | 'a value'
		S3     | 'a value'
		SX     | null
		String | null
	}

	class S1 {}
	class S2 extends S1 {}
	class S3 extends S2 {}
	class SX extends S1 {}
}