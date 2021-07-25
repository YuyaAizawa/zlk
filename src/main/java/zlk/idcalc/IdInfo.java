package zlk.idcalc;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.common.Type;

/**
 * 識別子情報．環境で利用して重複や未定義を防ぐ．
 * @author YuyaAizawa
 *
 */
@SuppressWarnings("preview")
public sealed interface IdInfo
permits IdFun, IdArg, IdBuiltin {

	int id();
	String name();
	Type type();

	<R> R map(
			Function<IdFun, R> forFun,
			Function<IdArg, R> forArg,
			Function<IdBuiltin, R> forBuiltin);

	void match(
			Consumer<IdFun> forFun,
			Consumer<IdArg> forArg,
			Consumer<IdBuiltin> forBuiltin);
}
