package zlk.recon;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import zlk.recon.constraint.Content.FlexVar;

/**
 * 型変数に被り名のない名前を与えるためのカウンター
 * 制約抽出と推論で一貫する必要がある
 */
public final class FreshFlex {
	private AtomicInteger freshId = new AtomicInteger();

	public Variable getVariable(String name, int letRank) {
		return Variable.ofFlex(freshId.getAndIncrement(), Optional.of(name), letRank);
	}

	public Variable getVariable() {
		return Variable.ofFlex(freshId.getAndIncrement(), Optional.empty(), 0);
	}

	public FlexVar getContent(String name) {
		return new FlexVar(freshId.getAndIncrement(), Optional.of(name));
	}

	public FlexVar getContent() {
		return new FlexVar(freshId.getAndIncrement(), Optional.empty());
	}
}
