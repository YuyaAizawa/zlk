package zlk.idcalc;

import zlk.common.Type;

public record IcPCtorArg(
		IcPattern pattern,
		Type type // for cache
) {}