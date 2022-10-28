package zlk.idcalc;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface IcExp extends PrettyPrintable, LocationHolder
permits IcCnst, IcVar, IcApp, IcIf, IcLet, IcLamb {

	default <R> R fold(
			Function<? super IcCnst, ? extends R> forCnst,
			Function<? super IcVar, ? extends R> forVar,
			Function<? super IcApp, ? extends R> forApp,
			Function<? super IcIf, ? extends R> forIf,
			Function<? super IcLet, ? extends R> forLet,
			Function<? super IcLamb, ? extends R> forLamb) {
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
		} else if(this instanceof IcLamb lamb) {
			return forLamb.apply(lamb);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	default void match(
			Consumer<? super IcCnst> forCnst,
			Consumer<? super IcVar> forVar,
			Consumer<? super IcApp> forApp,
			Consumer<? super IcIf> forIf,
			Consumer<? super IcLet> forLet,
			Consumer<? super IcLamb> forLamb) {
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
		} else if(this instanceof IcLamb lamb) {
			forLamb.accept(lamb);
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

	@Override
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