package zlk.compiler.ir.reuse.anf;

import zlk.compiler.ir.reuse.LocalVar;
import zlk.compiler.source.Location;
import zlk.compiler.source.LocationHolder;

public record AnfBind(
		LocalVar dst,
		AnfRhs rhs,
		Location loc
) implements LocationHolder {}
