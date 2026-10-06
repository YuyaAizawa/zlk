package zlk.compiler.ir.reuse.anf;

import zlk.compiler.ir.reuse.LocalVar;
import zlk.compiler.source.Location;
import zlk.compiler.source.LocationHolder;
import zlk.util.collection.Seq;

public record AnfBlock(
		Seq<AnfBind> binds,
		LocalVar result,
		Location loc
) implements LocationHolder {}
