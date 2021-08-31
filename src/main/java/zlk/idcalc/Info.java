package zlk.idcalc;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import zlk.common.Type;

@SuppressWarnings("preview")
public sealed abstract class Info
permits InfoFun, InfoArg, InfoBuiltin {

	private Optional<String> name;
	private Type type;

	protected Info(String name, Type type) {
		this.name = Optional.of(name);
		this.type = type;
	}

	protected Info(Type type) {
		this.name = Optional.empty();
		this.type = type;
	}

	public Optional<String> name() {
		return name;
	}

	public Type type() {
		return type;
	}

	public final <R> R map(
			Function<InfoFun, R> forFun,
			Function<InfoArg, R> forArg,
			Function<InfoBuiltin, R> forBuiltin) {
		if(this instanceof InfoFun fun) {
			return forFun.apply(fun);
		} else if(this instanceof InfoArg arg) {
			return forArg.apply(arg);
		} else if(this instanceof InfoBuiltin builtin) {
			return forBuiltin.apply(builtin);
		} else {
			throw new Error(this.getClass().getName());
		}
	}

	public final void match(
			Consumer<InfoFun> forFun,
			Consumer<InfoArg> forArg,
			Consumer<InfoBuiltin> forBuiltin) {
		if(this instanceof InfoFun fun) {
			forFun.accept(fun);
		} else if(this instanceof InfoArg arg) {
			forArg.accept(arg);
		} else if(this instanceof InfoBuiltin builtin) {
			forBuiltin.accept(builtin);
		} else {
			throw new Error(this.getClass().getName());
		}
	}
}
