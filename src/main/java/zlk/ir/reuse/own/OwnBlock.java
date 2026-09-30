package zlk.ir.reuse.own;

import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.util.collection.Seq;

public record OwnBlock(
		Seq<OwnStmt> stmts,
		OwnUse result,
		Location loc
) implements LocationHolder {
	public OwnBlock insertStmts(Seq<? extends OwnStmt> head) {
		return new OwnBlock(Seq.concat(head, stmts), result, loc);
	}
}
