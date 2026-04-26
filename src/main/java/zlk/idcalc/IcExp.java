package zlk.idcalc;

import java.util.Optional;
import java.util.function.Consumer;

import zlk.common.ConstValue;
import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.idcalc.IcExp.IcApp;
import zlk.idcalc.IcExp.IcCase;
import zlk.idcalc.IcExp.IcCnst;
import zlk.idcalc.IcExp.IcIf;
import zlk.idcalc.IcExp.IcLamb;
import zlk.idcalc.IcExp.IcLet;
import zlk.idcalc.IcExp.IcVarCtor;
import zlk.idcalc.IcExp.IcVarForeign;
import zlk.idcalc.IcExp.IcVarLocal;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface IcExp extends PrettyPrintable, LocationHolder, ExpOrPattern
permits IcCnst, IcVarLocal, IcVarForeign, IcVarCtor, IcLamb, IcApp, IcIf, IcLet, IcCase {

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
			Seq<IcPattern> args,
			IcExp body,
			Location loc) implements IcExp {}

	record IcApp(
			IcExp fun,
			Seq<IcExp> args,
			Location loc) implements IcExp {}

	record IcIf(
			IcExp cond,
			IcExp exp1,
			IcExp exp2,
			Location loc) implements IcExp {}

	record IcLet(
			Seq<IcValDecl> defs,
			IcExp body,
			Location loc) implements IcExp {}

	record IcCase(
			IcExp target,
			Seq<IcCaseBranch> branches,
			Location loc) implements IcExp {}

	public default Optional<Id> getId() {
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

	/**
	 * 式を行きがけ順に捜査する
	 * @param action
	 */
	default void walk(Consumer<? super IcExp> action) {
		action.accept(this);
		switch (this) {
		case IcLamb(Seq<IcPattern> _, IcExp body, Location _) ->
			body.walk(action);
		case IcApp(IcExp fun, Seq<IcExp> args, Location _) -> {
			fun.walk(action);
			args.forEach(arg -> arg.walk(action));
		}
		case IcIf(IcExp cond, IcExp thenExp, IcExp elseExp, Location _) -> {
			cond.walk(action);
			thenExp.walk(action);
			elseExp.walk(action);
		}
		case IcLet(Seq<IcValDecl> decls, IcExp body, Location _) -> {
			decls.forEach(decl -> decl.body().walk(action));
			body.walk(action);
		}
		case IcCase(IcExp target, Seq<IcCaseBranch> branches, Location _) -> {
			target.walk(action);
			branches.forEach(branch -> branch.body().walk(action));
		}
		default -> {}
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
		case IcLamb(Seq<IcPattern> args, IcExp body, Location _) -> {
			pp.append("\\");
			args.forEach(arg -> {
				pp.append(arg).append(" ");
			});
			pp.append("-> ");
			pp.append(body);
		}
		case IcApp(IcExp fun, Seq<IcExp> args, Location _) -> {
			switch(fun) {
			case IcCnst _, IcVarLocal _, IcVarForeign _, IcVarCtor _, IcApp _ -> {
				pp.append(fun);
			}
			case IcLamb _ -> {
				pp.append("(").append(fun).append(")");
			}
			case IcIf _, IcLet _, IcCase _ -> {
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
				case IcIf _, IcLet _, IcCase _ -> {
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
		case IcLet(Seq<IcValDecl> decls, IcExp body, Location _) -> {
			pp.append("let").endl();
			pp.indent(() -> {
				decls.forEach(decl -> {
					pp.append(decl).endl();
				});
			});
			pp.append("in");
			pp.indent(() -> {
				pp.endl().append(body);
			});
		}
		case IcCase(IcExp target, Seq<IcCaseBranch> branches, Location _) -> {
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
