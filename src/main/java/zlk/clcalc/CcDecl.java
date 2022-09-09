package zlk.clcalc;

import zlk.common.Id;
import zlk.common.IdList;
import zlk.common.Type;
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
		CcExp body)
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
