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
		Id var,
		CcExp boundExp,
		CcExp body,
		Location loc)
implements CcExp, PrettyPrintable {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("let:").endl();
		pp.indent(() -> {
			pp.append("var: ").append(var).endl();
			pp.append("boundExp:").endl();
			pp.indent(() -> {
				pp.append(boundExp).endl();
			});
			pp.append("body:").endl();
			pp.indent(() -> {
				pp.append(body);
			});
		});
	}
}
