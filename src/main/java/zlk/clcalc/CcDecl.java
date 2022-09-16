package zlk.clcalc;

import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.type.Type;
import zlk.util.Location;
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
		Type type,
		CcExp body,
		Location loc)
implements PrettyPrintable {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("decl:").endl().inc();
		pp.field("id", id);
		pp.field("args", args);
		pp.field("type", type);
		pp.field("body", body);
		pp.dec();
	}
}
