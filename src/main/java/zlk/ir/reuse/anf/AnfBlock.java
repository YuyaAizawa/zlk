package zlk.ir.reuse.anf;

import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.ir.reuse.LocalVar;
import zlk.util.collection.Seq;

public record AnfBlock(
		Seq<AnfBind> binds,
		LocalVar result,
		Location loc
) implements LocationHolder {}
