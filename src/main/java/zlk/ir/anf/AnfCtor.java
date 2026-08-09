package zlk.ir.anf;

import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.util.collection.Seq;

public record AnfCtor(
		Id id,
		Seq<Type> args,
		Location loc
) implements LocationHolder {}
