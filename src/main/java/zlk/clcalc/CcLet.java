package zlk.clcalc;

import zlk.common.id.Id;
import zlk.util.Location;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * ローカル変数になる部分
 *
 * @author YuyaAizawa
 */
public record CcLet(
		Id boundVar,
		CcExp boundExp,
		CcExp mainExp,
		Location loc)
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
