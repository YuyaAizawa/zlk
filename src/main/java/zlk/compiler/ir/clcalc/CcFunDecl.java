package zlk.compiler.ir.clcalc;

import zlk.compiler.id.Id;
import zlk.compiler.source.Location;
import zlk.compiler.source.LocationHolder;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * メソッドになる部分
 *
 * @author YuyaAizawa
 */
public record CcFunDecl(
		Id id,
		Seq<CcPattern> args,
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
			pp.append(PrettyPrintable.join(args, ", "));
			pp.append("]").endl();
			pp.append("body:").endl();
			pp.indent(() -> {
				pp.append(body);
			});
		});
	}
}
