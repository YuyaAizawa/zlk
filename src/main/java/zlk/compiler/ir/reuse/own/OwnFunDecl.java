package zlk.compiler.ir.reuse.own;

import zlk.compiler.id.Id;
import zlk.compiler.source.Location;
import zlk.compiler.source.LocationHolder;
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
