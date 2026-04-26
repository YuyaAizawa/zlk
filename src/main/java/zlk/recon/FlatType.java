package zlk.recon;

import java.util.function.Function;

import zlk.common.Type;
import zlk.common.id.Id;
import zlk.recon.FlatType.CtorApp1;
import zlk.recon.FlatType.Fun1;
import zlk.recon.constraint.Content;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 単一化後の型
 */
public sealed interface FlatType extends PrettyPrintable
permits CtorApp1, Fun1 {
	record CtorApp1(Id id, Seq<Variable> args) implements FlatType {} // TODO: App1?
	record Fun1(Variable arg, Variable ret) implements FlatType {}

	default FlatType traverse(Function<Variable, Variable> f) {
		return switch(this) {
		case CtorApp1(Id id, Seq<Variable> args) -> new CtorApp1(id, args.map(f));
		case Fun1(Variable arg, Variable ret) -> new Fun1(f.apply(arg), f.apply(ret));
		};
	}

	default Type toType() {
		return switch(this) {
		case CtorApp1(Id id, Seq<Variable> args) -> {
			if(id.equals(Type.BOOL.ctor())) {
				yield Type.BOOL;
			}
			if(id.equals(Type.I32.ctor())) {
				yield Type.I32;
			}
			// TODO: user defined type

			yield Type.arrow(
					args.map(Variable::toType),
					new Type.CtorApp(id));
		}
		case Fun1(Variable arg, Variable ret) ->
				new Type.Arrow(arg.toType(), ret.toType());
		};
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case CtorApp1(Id id, Seq<Variable> args) -> {
			pp.append(id);
			for(Variable arg : args) {
				pp.append(" ");
				if(arg.get().content instanceof Content.Structure structure
						&& structure.flatType() instanceof CtorApp1 ctorApp
						&& ctorApp.args.size() > 0) {
					// 複数トークンはカッコが要る
					pp.append("(").append(arg).append(")");
				} else {
					pp.append(arg);
				}
			}
		}
		case Fun1(Variable arg, Variable ret) -> {
			pp.append(arg).append(" -> ").append(ret);
		}
		}
	}
}

