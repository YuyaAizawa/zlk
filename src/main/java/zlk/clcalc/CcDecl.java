package zlk.clcalc;

import java.util.List;

import zlk.common.id.Id;
import zlk.idcalc.IcPattern;
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
		List<IcPattern> args,
		CcExp body,
		Location loc)
implements PrettyPrintable, LocationHolder {

	public int arity() {
		return args.size();
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("decl:").endl().inc();
		pp.field("id", id);
		pp.append("args: [").oneline(args, ", ").append("]").endl();
		pp.field("body", body);
		pp.dec();
	}
}
