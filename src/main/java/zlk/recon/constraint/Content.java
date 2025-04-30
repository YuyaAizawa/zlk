package zlk.recon.constraint;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import zlk.common.id.Id;
import zlk.recon.FlatType;
import zlk.recon.FlatType.CtorApp1;
import zlk.recon.FlatType.*;
import zlk.recon.Variable;
import zlk.recon.constraint.Content.FlexVar;
import zlk.recon.constraint.Content.Structure;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 推論中の型変数の暫定的な内容．
 * 値は，自由変数（FlexVar），または具象的な構造（Structure）．
 */
public sealed interface Content extends PrettyPrintable
permits FlexVar, Structure, Content.Error {

	/**
	 * 自由変数．
	 *
	 * @param id   変数の一意な識別子
	 * @param name この変数の推奨表示名
	 */
	record FlexVar(int id, Optional<String> name) implements Content {}

	/**
	 * 構造を持った型．
	 *
	 * @param FlatType 構造を持った型
	 */
	record Structure(FlatType flatType) implements Content {
		public Structure traverse(Function<Variable, Variable> f) {
			switch(flatType) {
			case CtorApp1(Id id, List<Variable> args) -> {
				return new Structure(new CtorApp1(id, args.stream().map(f).toList()));
			}
			case Fun1(Variable arg, Variable ret) -> {
				return new Structure(new Fun1(f.apply(arg), f.apply(ret)));
			}
			}
		}
	}
	
	record Error() implements Content {}

	public static Content ctorApp(Id id, List<Variable> args) {
		return new Structure(new CtorApp1(id, args));
	}

	public static Content fun(Variable arg, Variable ret) {
		return new Structure(new Fun1(arg, ret));
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case FlexVar(int id, Optional<String> name) ->
			pp.append("[").append(name.orElseGet(() -> String.valueOf(id))).append("]");
		case Structure(FlatType flatType) ->
			pp.append(flatType);
		case Content.Error() ->
			pp.append("!!error!!");
		}
	}
}
