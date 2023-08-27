package zlk.recon.constraint.type;

import static zlk.util.ErrorUtils.todo;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface Type extends PrettyPrintable
permits VarN, AppN, FunN {

	public static final Type BOOL = new AppN(zlk.common.type.Type.BOOL.id(), List.of());
	public static final Type I32  = new AppN(zlk.common.type.Type.I32.id() , List.of());

	default <R> R fold(
			Function<? super VarN, ? extends R> forVar,
			Function<? super AppN, ? extends R> forApp,
			Function<? super FunN, ? extends R> forFun) {
		if(this instanceof VarN var) {
			return forVar.apply(var);
		} else if(this instanceof AppN app) {
			return forApp.apply(app);
		} else if(this instanceof FunN fun) {
			return forFun.apply(fun);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	default void match(
			Consumer<? super VarN> forVar,
			Consumer<? super AppN> forApp,
			Consumer<? super FunN> forFun) {
		if(this instanceof VarN var) {
			forVar.accept(var);
		} else if(this instanceof AppN app) {
			forApp.accept(app);
		} else if(this instanceof FunN fun) {
			forFun.accept(fun);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	public static Type from(zlk.common.type.Type ty) {
		return ty.fold(
				atom -> {
					if(atom == zlk.common.type.Type.BOOL) {
						return BOOL;
					} else if(atom == zlk.common.type.Type.I32) {
						return I32;
					} else {
						return todo();
					}
				},
				(arg, ret) -> new FunN(from(arg), from(ret)),
				varId -> todo());
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		match(
				var -> {
					pp.append(var.var());
				},
				app -> {
					pp.append(app.id());
				},
				fun -> {
					fun.arg().match(
							v -> pp.append(v),
							a -> pp.append(a),
							f -> pp.append("(").append(f).append(")"));
					pp.append(" -> ");
					fun.ret().match(
							v -> pp.append(v),
							a -> pp.append(a),
							f -> pp.append(f));
				});
	}
}
