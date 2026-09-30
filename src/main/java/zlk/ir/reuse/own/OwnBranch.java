package zlk.ir.reuse.own;

import zlk.common.Location;
import zlk.common.LocationHolder;

public record OwnBranch(
		OwnPattern pattern,
		OwnBlock body,
		Location loc
) implements LocationHolder {}
