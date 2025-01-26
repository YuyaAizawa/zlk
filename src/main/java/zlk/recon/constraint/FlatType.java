package zlk.recon.constraint;

import static zlk.util.ErrorUtils.todo;

import java.util.List;
import java.util.function.Function;

import zlk.common.Type;
import zlk.common.id.Id;
import zlk.recon.Variable;
import zlk.recon.constraint.FlatType.App1;
import zlk.recon.constraint.FlatType.Fun1;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface FlatType extends PrettyPrintable
permits App1, Fun1 {
	record App1(Id id, List<Variable> args) implements FlatType {}
	record Fun1(Variable arg, Variable ret) implements FlatType {}

	default FlatType traverse(Function<Variable, Variable> f) {
		return switch(this) {
		case App1(Id id, List<Variable> args) -> new App1(id, args.stream().map(f).toList());
		case Fun1(Variable arg, Variable ret) -> new Fun1(f.apply(arg), f.apply(ret));
		};
	}

	default Type toType() {
		return switch(this) {
		case App1(Id id, List<Variable> args) -> {
			if(id.equals(Type.BOOL.id())) {
				yield Type.BOOL;
			}
			if(id.equals(Type.I32.id())) {
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
		case App1(Id id, _) -> {
			if(id.equals(Type.BOOL.id())) {
				pp.append(Type.BOOL);
				return;
			}
			if(id.equals(Type.I32.id())) {
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

