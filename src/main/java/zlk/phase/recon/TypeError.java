package zlk.phase.recon;

import zlk.common.Location;
import zlk.common.id.Id;
import zlk.phase.recon.TypeError.InfiniteType;
import zlk.phase.recon.TypeError.UnificationFailure;
import zlk.phase.recon.constraint.Constraint.Provenance;

public sealed interface TypeError
permits InfiniteType, UnificationFailure {
	/** 宣言の型を推論された型が自分自身を含む */
	record InfiniteType(Location location, Id id) implements TypeError {}

	/** 制約を満たす単一化が無い */
	record UnificationFailure(Provenance provenance, Mismatch.Reason reason) implements TypeError {}
}