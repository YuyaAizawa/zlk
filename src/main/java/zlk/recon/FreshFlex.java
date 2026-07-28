package zlk.recon;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import zlk.recon.Variable.Kind;
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

	/** ROW-kindのfresh flex変数を生成する．rankは0固定（互換）． */
	public Variable getVariable(Kind kind) {
		return getVariable(kind, 0);
	}

	/** 指定したkindとrankのfresh flex変数を生成する． */
	public Variable getVariable(Kind kind, int letRank) {
		return Variable.ofFlex(freshId.getAndIncrement(), Optional.empty(), letRank, kind);
	}

	/** 指定した名前，rank，kindのfresh flex変数を生成する． */
	public Variable getVariable(String name, int letRank, Kind kind) {
		return Variable.ofFlex(freshId.getAndIncrement(), Optional.of(name), letRank, kind);
	}

	public FlexVar getContent(String name) {
		return new FlexVar(freshId.getAndIncrement(), Optional.of(name));
	}

	public FlexVar getContent() {
		return new FlexVar(freshId.getAndIncrement(), Optional.empty());
	}
}