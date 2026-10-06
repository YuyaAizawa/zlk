package zlk.compiler.ir.reuse.own;

import zlk.compiler.source.Location;
import zlk.compiler.source.LocationHolder;

public record OwnBranch(
		OwnPattern pattern,
		OwnBlock body,
		Location loc
) implements LocationHolder {}
