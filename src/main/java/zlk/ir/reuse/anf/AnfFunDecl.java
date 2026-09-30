package zlk.ir.reuse.anf;

import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.common.id.Id;
import zlk.util.collection.Seq;

public record AnfFunDecl(
		Id id,
		Seq<AnfPattern> args,
		AnfBlock body,
		int localIdSize,
		Location loc) implements LocationHolder {

	public int arity() {
		return args.size();
	}
}
