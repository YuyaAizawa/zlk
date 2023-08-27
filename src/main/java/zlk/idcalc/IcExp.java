package zlk.idcalc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import zlk.common.id.Id;
import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface IcExp extends PrettyPrintable, LocationHolder
permits IcCnst, IcVar, IcForeign, IcAbs, IcApp, IcIf, IcLet, IcLetrec {

	default <R> R fold(
			Function<? super IcCnst, R> forCnst,
			Function<? super IcVar, R> forVar,
			Function<? super IcForeign, R> forForeign,
			Function<? super IcAbs, R> forAbs,
			Function<? super IcApp, R> forApp,
			Function<? super IcIf, R> forIf,
			Function<? super IcLet, R> forLet,
			Function<? super IcLetrec, R> forLetrec) {
		if(this instanceof IcCnst cnst) {
			return forCnst.apply(cnst);
		} else if(this instanceof IcVar id) {
			return forVar.apply(id);
		} else if(this instanceof IcForeign foreign) {
			return forForeign.apply(foreign);
		} else if(this instanceof IcAbs abs) {
			return forAbs.apply(abs);
		} else if(this instanceof IcApp app) {
			return forApp.apply(app);
		} else if(this instanceof IcIf ifExp) {
			return forIf.apply(ifExp);
		} else if(this instanceof IcLet let) {
			return forLet.apply(let);
		} else if(this instanceof IcLetrec letrec) {
			return forLetrec.apply(letrec);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	default void match(
			Consumer<? super IcCnst> forCnst,
			Consumer<? super IcVar> forVar,
			Consumer<? super IcForeign> forForeign,
			Consumer<? super IcAbs> forAbs,
			Consumer<? super IcApp> forApp,
			Consumer<? super IcIf> forIf,
			Consumer<? super IcLet> forLet,
			Consumer<? super IcLetrec> forLetrec) {
		if(this instanceof IcCnst cnst) {
			forCnst.accept(cnst);
		} else if(this instanceof IcVar id) {
			forVar.accept(id);
		} else if(this instanceof IcForeign foreign) {
			forForeign.accept(foreign);
		} else if(this instanceof IcAbs abs) {
			forAbs.accept(abs);
		} else if(this instanceof IcApp app) {
			forApp.accept(app);
		} else if(this instanceof IcIf ifExp) {
			forIf.accept(ifExp);
		} else if(this instanceof IcLet let) {
			forLet.accept(let);
		} else if(this instanceof IcLetrec letrec) {
			forLetrec.accept(letrec);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	default List<IcVar> fv(Collection<Id> known) {
		List<IcVar> acc = new ArrayList<>();
		Set<Id> knownSet = new HashSet<>(known);
		fv(acc, knownSet);
		return Collections.unmodifiableList(acc);
	}

	private void fv(List<IcVar> acc, Set<Id> known) {
		match(
				cnst -> {},
				var -> {
					if(!known.contains(var.id())) {
						acc.add(var);
					}},
				foreign -> {},
				abs -> {
					known.add(abs.id());
					abs.body().fv(acc, known);
				},
				app -> {
					app.fun().fv(acc, known);
					app.args().forEach(arg -> arg.fv(acc, known));
				},
				if_ -> {
					if_.cond().fv(acc, known);
					if_.exp1().fv(acc, known);
					if_.exp2().fv(acc, known);
				},
				let -> {
					known.add(let.decl().id());
					let.decl().args().forEach(arg -> arg.fv(acc, known));
					let.decl().body().fv(acc, known);
					let.body().fv(acc, known);
				},
				letrec -> {
					letrec.decls().forEach(decl -> {
						decl.args().forEach(arg -> arg.fv(acc, known));
						decl.body().fv(acc, known);
					});
					letrec.body().fv(acc, known);
				});
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