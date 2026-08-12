package zlk.ir.reuse.anf;

import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.ir.reuse.LocalVar;

public record AnfBind(
		LocalVar dst,
		AnfRhs rhs,
		Location loc
) implements LocationHolder {}
