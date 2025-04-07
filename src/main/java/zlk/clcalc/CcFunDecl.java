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
public record CcFunDecl(
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
		pp.append("func:").endl();
		pp.indent(() -> {
			pp.append("id: ").append(id).endl();
			pp.append("args: [");
			pp.append(PrettyPrintable.join(args.iterator(), ", "));
			pp.append("]").endl();
			pp.append("body:").endl();
			pp.indent(() -> {
				pp.append(body);
			});
		});
	}
}
