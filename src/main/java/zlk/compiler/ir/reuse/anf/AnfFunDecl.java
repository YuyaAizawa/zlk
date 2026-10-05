package zlk.compiler.ir.reuse.anf;

import zlk.compiler.id.Id;
import zlk.compiler.source.Location;
import zlk.compiler.source.LocationHolder;
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
