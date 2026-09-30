package zlk.ir.reuse.anf;

import zlk.common.Location;
import zlk.common.LocationHolder;

public record AnfBranch(
		AnfPattern pattern,
		AnfBlock body,
		Location loc
) implements LocationHolder {}
