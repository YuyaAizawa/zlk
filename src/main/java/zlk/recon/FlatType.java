package zlk.recon;

import static zlk.util.ErrorUtils.todo;

import java.util.List;
import java.util.function.Function;

import zlk.common.Type;
import zlk.common.id.Id;
import zlk.recon.FlatType.CtorApp1;
import zlk.recon.FlatType.Fun1;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 単一化後の型
 */
public sealed interface FlatType extends PrettyPrintable
permits CtorApp1, Fun1 {
	record CtorApp1(Id id, List<Variable> args) implements FlatType {}
	record Fun1(Variable arg, Variable ret) implements FlatType {}

	default FlatType traverse(Function<Variable, Variable> f) {
		return switch(this) {
		case CtorApp1(Id id, List<Variable> args) -> new CtorApp1(id, args.stream().map(f).toList());
		case Fun1(Variable arg, Variable ret) -> new Fun1(f.apply(arg), f.apply(ret));
		};
	}

	default Type toType() {
		return switch(this) {
		case CtorApp1(Id id, List<Variable> args) -> {
			if(id.equals(Type.BOOL.ctor())) {
				yield Type.BOOL;
			}
			if(id.equals(Type.I32.ctor())) {
				yield Type.I32;
			}
			// TODO: user defined type

			yield Type.arrow(
					args.stream().map(Variable::toAnnotation).toList(),
					new Type.Atom(id));
		}
		case Fun1(Variable arg, Variable ret) ->
				new Type.Arrow(arg.toType(), ret.toType());
		};
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case CtorApp1(Id id, _) -> {
			if(id.equals(Type.BOOL.ctor())) {
				pp.append(Type.BOOL);
				return;
			}
			if(id.equals(Type.I32.ctor())) {
				pp.append(Type.I32);
				return;
			}
			todo();
		}
		case Fun1(Variable arg, Variable ret) -> {
			pp.append(arg).append(" -> ").append(ret);
		}
		}
	}
}

