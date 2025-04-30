package zlk.ast;

import java.util.List;

import zlk.ast.Decl.ValDecl;
import zlk.ast.Exp.App;
import zlk.ast.Exp.Case;
import zlk.ast.Exp.Cnst;
import zlk.ast.Exp.If;
import zlk.ast.Exp.Lamb;
import zlk.ast.Exp.Let;
import zlk.ast.Exp.Var;
import zlk.common.ConstValue;
import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * パースした構文木の式を表す
 */
public sealed interface Exp extends PrettyPrintable, LocationHolder
permits Cnst, Var, Lamb, App, If, Let, Case {
	record Cnst(ConstValue value, Location loc) implements Exp {
			public Cnst(boolean value, Location loc) {
				this(value ? ConstValue.TRUE : ConstValue.FALSE, loc);
			}
			public Cnst(int value, Location loc) {
				this(new ConstValue.I32(value), loc);
			}
	}
	record Var(String name, Location loc) implements Exp {}
	record Lamb(List<Pattern> args, Exp body, Location loc) implements Exp {}
	record App(List<Exp> exps, 	Location loc) implements Exp {}
	record If(Exp cond, Exp thenExp, Exp elseExp, Location loc) implements Exp {}
	record Let(List<ValDecl> decls, Exp body, Location loc) implements Exp {}
	record Case(Exp exp, List<CaseBranch> branches, Location loc) implements Exp {}

	static boolean isIf(Exp exp) {
		return exp instanceof If;
	}

	static boolean isLet(Exp exp) {
		return exp instanceof Let;
	}

	/**
	 * Appends the string representation of this expression to specified printer.
	 * It does not terminate the line.
	 * @param pp printer to append string.
	 */
	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case Cnst(ConstValue value, _) -> {
			pp.append(value);
		}
		case Var(String name, _) -> {
			pp.append(name);
		}
		case Lamb(List<Pattern> args, Exp body, _) -> {
			pp.append("\\");
			args.forEach(arg ->
					pp.append(arg).append(" "));
			pp.append("-> ");
			pp.append(body);
		}
		case App(List<Exp> exps, _) -> {
			Exp hd = exps.get(0);
			switch(hd) {
			case Cnst _, Var _, App _ -> pp.append(hd);
			case Lamb _, If _, Let _, Case _ -> { pp
						.append("(").endl()
						.inc().append(hd).endl()
						.dec().append(")");
			}
			}
			for(int i = 1; i < exps.size(); i++) {
				pp.append(" ");
				Exp exp = exps.get(i);
				switch(exp) {
				case Cnst _, Var _ -> pp.append(exp);
				case App _ -> pp.append("(").append(exp).append(")");
				case Lamb _, If _, Let _, Case _ -> { pp
					.append("(").endl()
					.inc().append(exp).endl()
					.dec().append(")");
				}
				}
			}
		}
		case If(Exp cond, Exp exp1, Exp exp2, _) -> {
			pp.append("if ").append(cond).append(" then").endl();
			pp.inc().append(exp1).dec().endl();
			pp.append("else");
			if(Exp.isIf(exp2)) {
				pp.append(" ").append(exp2);
			} else {
				pp.endl();
				pp.inc().append(exp2).dec();
			}
		}
		case Let(List<ValDecl> decls, Exp body, _) -> {
			pp.append("let").endl();

			pp.inc();
			for(ValDecl decl : decls) {
				pp.append(decl).endl();
			}
			pp.dec();

			pp.append("in");
			if(Exp.isLet(body)) {
				pp.append(" ").append(body);
			} else {
				pp.endl();
				pp.indent(() -> {
					pp.append(body);
				});
			}
		}
		case Case(Exp exp, List<CaseBranch> branches, _) -> {
			pp.append("case ").append(exp).append(" of");
			for(CaseBranch branch : branches) {
				pp.endl().append(branch);
			}
		}
		}
	}
}