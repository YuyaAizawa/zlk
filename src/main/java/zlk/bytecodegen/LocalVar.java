package zlk.bytecodegen;

import zlk.common.Type;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * バイトコード上で local variable として扱われる変数
 * @author YuyaAizawa
 *
 */
public record LocalVar(
		int idx,
		Type type  // TODO 時効時型に
) implements PrettyPrintable {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("LocalVariable ").append(idx).append(type);
	}
}
