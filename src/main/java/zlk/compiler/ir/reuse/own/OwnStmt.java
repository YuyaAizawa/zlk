package zlk.compiler.ir.reuse.own;

import zlk.compiler.ir.reuse.LocalVar;
import zlk.compiler.source.Location;
import zlk.compiler.source.LocationHolder;

public sealed interface OwnStmt extends LocationHolder {
	// TODO Deconstructを入れるならここ

	/**
	 * 新しい局所変数を束縛する．
	 * dst.id == empty は single-use を表す．
	 * ownershipは dst が持つ参照の所有状態を表す．
	 */
	record Bind(
			LocalVar dst,
			Ownership ownership,
			OwnRhs rhs,
			Location loc
	) implements OwnStmt {}

	/** 参照カウントを1増やし，同じLocalVarから追加の所有参照を作る． */
	record Dup(LocalVar var, Location loc) implements OwnStmt {}

	/** この関数が保持する所有参照を破棄する． */
	record Drop(LocalVar var, Location loc) implements OwnStmt {}
}
