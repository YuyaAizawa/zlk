package zlk.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;

import zlk.repopt.RepKey;

public final class RepKeysTester {

	private EnumSet<RepKey> repKeys;

	RepKeysTester(EnumSet<RepKey> repKeys) {
		this.repKeys = repKeys;
	}

	public void is(RepKey... expects) {
		EnumSet<RepKey> expected = EnumSet.noneOf(RepKey.class);
		for(RepKey r : expects) {
			expected.add(r);
		}
		assertEquals(expected, repKeys);
	}

	@Override
	public String toString() {
		return repKeys.toString();
	}
}
