package zlk.clcalc;

import zlk.common.Id;
import zlk.common.Type;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record CcLet(
		Id boundVar,
		CcExp boundExp,
		CcExp mainExp,
		Type varType)
implements CcExp, PrettyPrintable {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("let:").endl().inc();
		pp.field("boundVar", boundVar);
		pp.field("boundExp", boundExp);
		pp.field("mainExp", mainExp);
		pp.dec();
	}
}
