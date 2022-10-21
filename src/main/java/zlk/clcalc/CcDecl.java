package zlk.clcalc;

import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * メソッドになる部分
 *
 * @author YuyaAizawa
 */
public record CcDecl(
		Id id,
		IdList args,
		CcExp body,
		Location loc)
implements PrettyPrintable, LocationHolder {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("decl:").endl().inc();
		pp.field("id", id);
		pp.field("args", args);
		pp.field("body", body);
		pp.dec();
	}
}
