package zlk.idcalc;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.Location;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface IcExp extends PrettyPrintable
permits IcCnst, IcVar, IcApp, IcIf, IcLet {

	default <R> R fold(
			Function<IcCnst, R> forCnst,
			Function<IcVar, R> forVar,
			Function<IcApp, R> forApp,
			Function<IcIf, R> forIf,
			Function<IcLet, R> forLet) {
		if(this instanceof IcCnst cnst) {
			return forCnst.apply(cnst);
		} else if(this instanceof IcVar id) {
			return forVar.apply(id);
		} else if(this instanceof IcApp app) {
			return forApp.apply(app);
		} else if(this instanceof IcIf ifExp) {
			return forIf.apply(ifExp);
		} else if(this instanceof IcLet let) {
			return forLet.apply(let);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	default void match(
			Consumer<IcCnst> forCnst,
			Consumer<IcVar> forVar,
			Consumer<IcApp> forApp,
			Consumer<IcIf> forIf,
			Consumer<IcLet> forLet) {
		if(this instanceof IcCnst cnst) {
			forCnst.accept(cnst);
		} else if(this instanceof IcVar id) {
			forVar.accept(id);
		} else if(this instanceof IcApp app) {
			forApp.accept(app);
		} else if(this instanceof IcIf ifExp) {
			forIf.accept(ifExp);
		} else if(this instanceof IcLet let) {
			forLet.accept(let);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	static boolean isVar(IcExp exp) {
		return exp instanceof IcVar;
	}

	static boolean isIf(IcExp exp) {
		return exp instanceof IcIf;
	}

	static boolean isLet(IcExp exp) {
		return exp instanceof IcLet;
	}

	Location loc();

	/**
	 * Appends the string representation of this expression to specified printer.
	 * It does not terminate the line.
	 * @param pp printer to append string.
	 */
	@Override
	void mkString(PrettyPrinter pp);

	public static String buildString(IcExp exp) {
		StringBuilder sb = new StringBuilder();
		exp.pp(sb);
		return sb.toString();
	}
}