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
permits IcCnst, IcVarLocal, IcVarForeign, IcVarCtor, IcAbs, IcApp, IcIf, IcLet, IcLetrec, IcCase {

	default <R> R fold(
			Function<? super IcCnst, R> forCnst,
			Function<? super IcVarLocal, R> forVar,
			Function<? super IcVarForeign, R> forForeign,
			Function<? super IcVarCtor, R> forCtor,
			Function<? super IcAbs, R> forAbs,
			Function<? super IcApp, R> forApp,
			Function<? super IcIf, R> forIf,
			Function<? super IcLet, R> forLet,
			Function<? super IcLetrec, R> forLetrec,
			Function<? super IcCase, R> forCase) {
		if(this instanceof IcCnst cnst) {
			return forCnst.apply(cnst);
		} else if(this instanceof IcVarLocal id) {
			return forVar.apply(id);
		} else if(this instanceof IcVarForeign foreign) {
			return forForeign.apply(foreign);
		} else if(this instanceof IcVarCtor ctor) {
			return forCtor.apply(ctor);
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
		} else if(this instanceof IcCase case_) {
			return forCase.apply(case_);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	default void match(
			Consumer<? super IcCnst> forCnst,
			Consumer<? super IcVarLocal> forVar,
			Consumer<? super IcVarForeign> forForeign,
			Consumer<? super IcVarCtor> forCtor,
			Consumer<? super IcAbs> forAbs,
			Consumer<? super IcApp> forApp,
			Consumer<? super IcIf> forIf,
			Consumer<? super IcLet> forLet,
			Consumer<? super IcLetrec> forLetrec,
			Consumer<? super IcCase> forCase) {
		if(this instanceof IcCnst cnst) {
			forCnst.accept(cnst);
		} else if(this instanceof IcVarLocal id) {
			forVar.accept(id);
		} else if(this instanceof IcVarForeign foreign) {
			forForeign.accept(foreign);
		} else if(this instanceof IcVarCtor ctor) {
			forCtor.accept(ctor);
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
		} else if(this instanceof IcCase case_) {
			forCase.accept(case_);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	default List<IcVarLocal> fv(Collection<Id> known) {
		List<IcVarLocal> acc = new ArrayList<>();
		Set<Id> knownSet = new HashSet<>(known);
		fv(acc, knownSet);
		return Collections.unmodifiableList(acc);
	}

	private void fv(List<IcVarLocal> acc, Set<Id> known) {
		match(
				cnst -> {},
				var -> {
					if(!known.contains(var.id())) {
						acc.add(var);
					}},
				foreign -> {},
				ctor -> {},
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
					let.decl().args().forEach(pat -> pat.addVars(known));
					let.decl().body().fv(acc, known);
					let.body().fv(acc, known);
				},
				letrec -> {
					letrec.decls().forEach(decl -> {
						decl.args().forEach(pat -> pat.addVars(known));
						decl.body().fv(acc, known);
					});
					letrec.body().fv(acc, known);
				},
				case_ -> {
					case_.target().fv(acc, known);
					for(IcCaseBranch branch: case_.branches()) {
						branch.pattern().addVars(known);
						branch.body().fv(acc, known);
					}
				});
	}

	static boolean isVar(IcExp exp) {
		return exp instanceof IcVarLocal;
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
}