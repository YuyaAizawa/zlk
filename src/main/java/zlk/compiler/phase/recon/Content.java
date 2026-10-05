package zlk.compiler.phase.recon;

import java.util.Optional;
import java.util.function.Function;

import zlk.compiler.id.Id;
import zlk.compiler.phase.recon.Content.FlexVar;
import zlk.compiler.phase.recon.Content.RigidVar;
import zlk.compiler.phase.recon.Content.Structure;
import zlk.compiler.phase.recon.FlatType.CtorApp1;
import zlk.compiler.phase.recon.FlatType.Fun1;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 推論中の型変数の暫定的な内容．
 * 値は，自由変数（FlexVar），または具象的な構造（Structure）．
 */
public sealed interface Content extends PrettyPrintable
permits FlexVar, RigidVar, Structure, Content.Error {

	/**
	 * 自由変数
	 *
	 * @param id   変数の一意な識別子
	 * @param name この変数の推奨表示名
	 */
	record FlexVar(int id, Optional<String> name) implements Content {}

	record RigidVar(String name) implements Content {}

	/**
	 * 構造を持った型
	 *
	 * @param FlatType 構造を持った型
	 */
	record Structure(FlatType flatType) implements Content {
		public Structure traverse(Function<Variable, Variable> f) {
			// FlatType.traverseへ委譲
			return new Structure(flatType.traverse(f));
		}
	}

	record Error() implements Content {}

	public static Content ctorApp(Id id, Seq<Variable> args) {
		return new Structure(new CtorApp1(id, args));
	}

	public static Content fun(Variable arg, Variable ret) {
		return new Structure(new Fun1(arg, ret));
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case FlexVar(int id, Optional<String> _) ->
			pp.append("[").append(id).append("]");
		case RigidVar(String name) ->
			pp.append("<").append(name).append(">");
		case Structure(FlatType flatType) ->
			pp.append(flatType);
		case Content.Error() ->
			pp.append("!!error!!");
		}
	}
}