package zlk.recon;

import static zlk.util.ErrorUtils.neverHappen;
import static zlk.util.ErrorUtils.todo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.common.type.Type;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcVar;
import zlk.util.AssocList;

public final class TypeReconstructor {

	public static record ReconResult(
			IdMap<TypeAnnotation> types,
			List<TypeError> errs
	) {}

	public static record TypeAnnotation(
			IdList freeVars,
			Type type
	) {}

	public static record TypeError() {}

	public static Type recon
}
