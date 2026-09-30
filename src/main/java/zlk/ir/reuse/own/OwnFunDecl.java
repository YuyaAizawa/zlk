package zlk.ir.reuse.own;

import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.common.id.Id;
import zlk.util.collection.Seq;

public record OwnFunDecl(
		Id id,
		Seq<OwnPattern> args,
		OwnBlock body,
		int localIdSize,
		Location loc) implements LocationHolder {

	public int arity() {
		return args.size();
	}
}
