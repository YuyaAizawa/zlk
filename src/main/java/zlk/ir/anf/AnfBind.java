package zlk.ir.anf;

import zlk.common.Location;
import zlk.common.LocationHolder;

public record AnfBind(
		AnfVar dst,
		AnfRhs rhs,
		Location loc
) implements LocationHolder {}
