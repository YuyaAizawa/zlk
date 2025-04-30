package zlk.idcalc;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import zlk.common.ConstValue;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.idcalc.IcExp.IcApp;
import zlk.idcalc.IcExp.IcCase;
import zlk.idcalc.IcExp.IcCnst;
import zlk.idcalc.IcExp.IcIf;
import zlk.idcalc.IcExp.IcLamb;
import zlk.idcalc.IcExp.IcLet;
import zlk.idcalc.IcExp.IcLetrec;
import zlk.idcalc.IcExp.IcVarCtor;
import zlk.idcalc.IcExp.IcVarForeign;
import zlk.idcalc.IcExp.IcVarLocal;
import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface IcExp extends PrettyPrintable, LocationHolder
permits IcCnst, IcVarLocal, IcVarForeign, IcVarCtor, IcLamb, IcApp, IcIf, IcLet, IcLetrec, IcCase {

	record IcCnst(
			ConstValue value,
			Location loc) implements IcExp {
		Type type() {
			return value.type();
		}
	}

	record IcVarLocal(
			Id id,
			Location loc) implements IcExp {}

	record IcVarForeign(
			Id id,
			Type type,
			Location loc) implements IcExp {}

	record IcVarCtor(
			Id id,
			Type type,
			Location loc) implements IcExp {}

	record IcLamb(
			List<IcPattern> args,
			IcExp body,
			Location loc) implements IcExp {}

	record IcApp(
			IcExp fun,
			List<IcExp> args,
			Location loc) implements IcExp {}

	record IcIf(
			IcExp cond,
			IcExp exp1,
			IcExp exp2,
			Location loc) implements IcExp {}

	record IcLet(
			IcValDecl decl,
			IcExp body,
			Location loc) implements IcExp {}

	record IcLetrec(
			List<IcValDecl> decls,
			IcExp body,
			Location loc) implements IcExp {}

	record IcCase(
			IcExp target,
			List<IcCaseBranch> branches,
			Location loc) implements IcExp {}

	default IdList fv(Collection<Id> known) {
		IdList acc = new IdList();
		Set<Id> knownSet = new HashSet<>(known);
		fv(acc, knownSet);
		return acc;
	}

	public default Optional<Id> getName() {
		switch (this) {
		case IcVarLocal(Id id, Location _) -> {
			return Optional.of(id);
		}
		case IcVarForeign(Id id, Type _, Location _) -> {
			return Optional.of(id);
		}
		case IcVarCtor(Id id, Type _, Location _) -> {
			return Optional.of(id);
		}
		default -> {
			return Optional.empty();
		}
		}
	}

	private void fv(IdList acc, Set<Id> known) {
		switch (this) {
		case IcCnst(ConstValue _, Location _) -> {}
		case IcVarLocal(Id id, Location _) -> {
			if (!known.contains(id)) {
				acc.add(id);
			}
		}
		case IcVarForeign(Id _, Type _, Location _) -> {}
		case IcVarCtor(Id _, Type _, Location _) -> {}
		case IcLamb(List<IcPattern> args, IcExp body, Location _) -> {
			args.forEach(pat -> pat.accumulateVars(known));
			body.fv(acc, known);
		}
		case IcApp(IcExp fun, List<IcExp> args, Location _) -> {
			fun.fv(acc, known);
			args.forEach(arg -> arg.fv(acc, known));
		}
		case IcIf(IcExp cond, IcExp thenExp, IcExp elseExp, Location _) -> {
			cond.fv(acc, known);
			thenExp.fv(acc, known);
			elseExp.fv(acc, known);
		}
		case IcLet(IcValDecl decl, IcExp body, Location _) -> {
			known.add(decl.id());
			decl.args().forEach(pat -> pat.accumulateVars(known));
			decl.body().fv(acc, known);
			body.fv(acc, known);
		}
		case IcLetrec(List<IcValDecl> decls, IcExp body, Location _) -> {
			decls.forEach(decl -> {
				decl.args().forEach(pat -> pat.accumulateVars(known));
				decl.body().fv(acc, known);
			});
			body.fv(acc, known);
		}
		case IcCase(IcExp target, List<IcCaseBranch> branches, Location _) -> {
			target.fv(acc, known);
			branches.forEach(branch -> {
				branch.pattern().accumulateVars(known);
				branch.body().fv(acc, known);
			});
		}
		}
	}

	/**
	 * Appends the string representation of this expression to specified printer.
	 * It does not terminate the line.
	 * @param pp printer to append string.
	 */
	@Override
	default void mkString(PrettyPrinter pp) {
		switch (this) {
		case IcCnst(ConstValue value, Location _) -> {
			pp.append(value);
		}
		case IcVarLocal(Id id, Location _) -> {
			pp.append(id);
		}
		case IcVarForeign(Id id, Type _, Location _) -> {
			pp.append(id);
		}
		case IcVarCtor(Id id, Type _, Location _) -> {
			pp.append(id);
		}
		case IcLamb(List<IcPattern> args, IcExp body, Location _) -> {
			pp.append("\\");
			args.forEach(arg -> {
				pp.append(arg).append(" ");
			});
			pp.append("-> ");
			pp.append(body);
		}
		case IcApp(IcExp fun, List<IcExp> args, Location _) -> {
			switch(fun) {
			case IcCnst _, IcVarLocal _, IcVarForeign _, IcVarCtor _, IcApp _ -> {
				pp.append(fun);
			}
			case IcLamb _ -> {
				pp.append("(").append(fun).append(")");
			}
			case IcIf _, IcLet _, IcLetrec _, IcCase _ -> {
				pp.append("(").endl();
				pp.indent(() -> pp.append(fun).endl());
				pp.append(")");
			}
			}
			args.forEach(arg -> {
				pp.append(" ");
				switch(arg) {
				case IcCnst _, IcVarLocal _, IcVarForeign _, IcVarCtor _ -> {
					pp.append(arg);
				}
				case IcLamb _, IcApp _ -> {
					pp.append("(").append(arg).append(")");
				}
				case IcIf _, IcLet _, IcLetrec _, IcCase _ -> {
					pp.append("(").endl();
					pp.indent(() -> pp.append(arg).endl());
					pp.append(")");
				}
				}
			});
		}
		case IcIf(IcExp cond, IcExp thenExp, IcExp elseExp, Location _) -> {
			pp.append("if ").append(cond).append(" then").endl();
			pp.indent(() -> {
				pp.append(thenExp).endl();
			});
			pp.append("else");
			if(elseExp instanceof IcIf) {
				pp.append(" ").append(elseExp);
			} else {
				pp.indent(() -> {
					pp.endl().append(elseExp);
				});
			}
		}
		case IcLet(IcValDecl decl, IcExp body, Location _) -> {
			pp.append("let").endl();
			pp.indent(() -> {
				pp.append(decl).endl();
			});
			pp.append("in");
			if(body instanceof IcLet | body instanceof IcLetrec) {
				pp.append(" ").append(body);
			} else {
				pp.indent(() -> {
					pp.endl().append(body);
				});
			}
		}
		case IcLetrec(List<IcValDecl> decls, IcExp body, Location _) -> {
			pp.append("letrec").endl();
			pp.indent(() -> {
				decls.forEach(decl -> {
					pp.append(decl).endl();
				});
			});
			pp.append("in");
			if(body instanceof IcLet | body instanceof IcLetrec) {
				pp.append(" ").append(body);
			} else {
				pp.indent(() -> {
					pp.endl().append(body);
				});
			}
		}
		case IcCase(IcExp target, List<IcCaseBranch> branches, Location _) -> {
			pp.append("case ").append(target).append(" of");
			pp.indent(() -> {
				branches.forEach(branch -> {
					pp.endl().append(branch);
				});
			});
		}
		}
	}
}