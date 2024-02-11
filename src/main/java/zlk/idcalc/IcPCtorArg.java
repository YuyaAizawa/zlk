package zlk.idcalc;

import zlk.common.type.Type;

public record IcPCtorArg(
		IcPattern pattern,
		Type type // for cache
) {}