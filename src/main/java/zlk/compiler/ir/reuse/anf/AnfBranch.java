package zlk.compiler.ir.reuse.anf;

import zlk.compiler.source.Location;
import zlk.compiler.source.LocationHolder;

public record AnfBranch(
		AnfPattern pattern,
		AnfBlock body,
		Location loc
) implements LocationHolder {}
