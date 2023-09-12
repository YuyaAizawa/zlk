package zlk.recon.flattype;

import static zlk.util.ErrorUtils.todo;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.common.type.TyArrow;
import zlk.common.type.TyAtom;
import zlk.common.type.Type;
import zlk.recon.Variable;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface FlatType extends PrettyPrintable
permits App1, Fun1 {
	default <R> R fold(
			Function<? super App1, ? extends R> forApp,
			Function<? super Fun1, ? extends R> forFun) {
		if(this instanceof App1 app) {
			return forApp.apply(app);
		} else if(this instanceof Fun1 fun) {
			return forFun.apply(fun);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	default void match(
			Consumer<? super App1> forApp,
			Consumer<? super Fun1> forFun) {
		if(this instanceof App1 app) {
			forApp.accept(app);
		} else if(this instanceof Fun1 fun) {
			forFun.accept(fun);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	default FlatType traverse(Function<Variable, Variable> f) {
		return this.fold(
				app -> new App1(app.id(), app.args().stream().map(f).toList()),
				fun -> new Fun1(f.apply(fun.arg()), f.apply(fun.ret())));
	}

	default Type toType() {
		return fold(
				app -> {
					if(app.id().equals(Type.BOOL.id())) {
						return Type.BOOL;
					}
					if(app.id().equals(Type.I32.id())) {
						return Type.I32;
					}

					return Type.arrow(
							app.args().stream().map(Variable::toAnnotation).toList(),
							new TyAtom(app.id()));
				},
				fun -> new TyArrow(fun.arg().toType(), fun.ret().toType()));
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		match(
				app -> {
					if(app.id().equals(Type.BOOL.id())) {
						Type.BOOL.mkString(pp);
						return;
					}
					if(app.id().equals(Type.I32.id())) {
						Type.I32.mkString(pp);
						return;
					}
					todo();
				},
				fun -> {
					pp.append(fun.arg()).append(" -> ").append(fun.ret());
				});
	}
}

