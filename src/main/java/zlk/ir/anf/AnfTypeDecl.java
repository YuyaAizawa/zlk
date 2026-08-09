package zlk.ir.anf;

import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.common.id.Id;
import zlk.util.collection.Seq;

public record AnfTypeDecl(
		Id id,
		Seq<AnfCtor> ctors,
		Location loc
) implements LocationHolder {}
