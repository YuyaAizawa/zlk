package zlk.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

public final class CandidateTester<T extends Enum<T>> {

	private final EnumSet<T> actual;

	CandidateTester(EnumSet<T> actual) {
		this.actual = actual;
	}

	public void is(Set<T> expected) {
		assertEquals(expected, actual);
	}

	public void isEmpty() {
		assertTrue(actual.isEmpty());
	}
}
