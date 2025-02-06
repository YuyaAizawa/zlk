package zlk.clcalc;

import static zlk.util.pp.PrettyPrintable.join;

import java.util.List;

import zlk.clcalc.CcExp.CcApp;
import zlk.clcalc.CcExp.CcCase;
import zlk.clcalc.CcExp.CcCnst;
import zlk.clcalc.CcExp.CcIf;
import zlk.clcalc.CcExp.CcLet;
import zlk.clcalc.CcExp.CcMkCls;
import zlk.clcalc.CcExp.CcVar;
import zlk.common.ConstValue;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.util.Location;
import zlk.util.LocationHolder;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface CcExp extends PrettyPrintable, LocationHolder
permits CcCnst, CcVar, CcApp, CcMkCls, CcIf, CcLet, CcCase {

	record CcCnst(
			ConstValue value,
			Location loc) implements CcExp {
		Type type() {
			return value.type();
		}
	}

	record CcVar(
			Id id,
			Location loc) implements CcExp {}

	record CcApp(
			CcExp fun,
			List<CcExp> args,
			Location loc) implements CcExp {}

	record CcMkCls(
			Id clsFunc, // メソッド定義
			IdList caps, // キャプチャする変数
			Location loc) implements CcExp {}

	record CcIf(
			CcExp cond,
			CcExp thenExp,
			CcExp elseExp,
			Location loc) implements CcExp {}

	record CcLet(  // ローカル変数になる部分
			Id var,
			CcExp boundExp,
			CcExp body,
			Location loc) implements CcExp {}

	record CcCase(
			CcExp target,
			List<CcCaseBranch> branches,
			Location loc) implements CcExp {}

	default CcExp substId(IdMap<Id> map) {
		return switch (this) {
		case CcCnst _ -> {
			yield this;
		}
		case CcVar(Id id, Location loc) -> {
			yield new CcVar(map.getOrDefault(id, id), loc);
		}
		case CcApp(CcExp fun, List<CcExp> args, Location loc) -> {
			yield new CcApp(
					fun.substId(map),
					args.stream().map(arg -> arg.substId(map)).toList(),
					loc);
		}
		case CcMkCls(Id clsFunc, IdList caps, Location loc) -> {
			yield new CcMkCls(
					map.getOrDefault(clsFunc, clsFunc),
					caps.substId(map),
					loc);
		}
		case CcIf(CcExp cond, CcExp thenExp, CcExp elseExp, Location loc) -> {
			yield new CcIf(
					cond.substId(map),
					thenExp.substId(map),
					elseExp.substId(map),
					loc);
		}
		case CcLet(Id varName, CcExp boundExp, CcExp body, Location loc) -> {
			yield new CcLet(
					varName,
					boundExp.substId(map),
					body.substId(map),
					loc);
		}
		case CcCase(CcExp target, List<CcCaseBranch> branches, Location loc) -> {
			yield new CcCase(
					target.substId(map),
					branches.stream().map(branch -> branch.substId(map)).toList(),
					loc);
		}
		};
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch (this) {
		case CcCnst(ConstValue value, Location _) -> {
			pp.append("const: ").append(value);
		}
		case CcVar(Id id, Location _) -> {
			pp.append("var: ").append(id);
		}
		case CcApp(CcExp fun, List<CcExp> args, Location _) -> {
			pp.append("app:").endl();
			pp.indent(() -> {
				pp.append("funExp:").endl();
				pp.indent(() -> {
					pp.append(fun).endl();
				});
				pp.append("argExp:");
				pp.indent(() -> {
					args.forEach(arg -> {
						pp.endl().append(arg);
					});
				});
			});
		}
		case CcMkCls(Id clsFunc, IdList caps, Location _) -> {
			pp.append("mkCls:").endl();
			pp.indent(() -> {
				pp.append("clsFunc: ").append(clsFunc).endl();
				pp.append("caps: ").append("[").append(join(caps.iterator(), ", ")).append("]");
			});
		}
		case CcIf(CcExp cond, CcExp thenExp, CcExp elseExp, Location _) -> {
			pp.append("if:").endl();
			pp.indent(() -> {
				pp.append("cond:").endl();
				pp.indent(() -> {
					pp.append(cond).endl();
				});
				pp.append("then:").endl();
				pp.indent(() -> {
					pp.append(thenExp).endl();
				});
				pp.append("else:").endl();
				pp.indent(() -> {
					pp.append(elseExp);
				});
			});
		}
		case CcLet(Id varName, CcExp boundExp, CcExp body, Location _) -> {
			pp.append("let:").endl();
			pp.indent(() -> {
				pp.append("var: ").append(varName).endl();
				pp.append("boundExp:").endl();
				pp.indent(() -> {
					pp.append(boundExp).endl();
				});
				pp.append("body:").endl();
				pp.indent(() -> {
					pp.append(body);
				});
			});
		}
		case CcCase(CcExp target, List<CcCaseBranch> branches, Location _) -> {
			pp.append("case:").endl();
			pp.indent(() -> {
				pp.append("target:").endl();
				pp.indent(() -> {
					pp.append(target).endl();
				});
				pp.append("branches:");
				pp.indent(() -> {
					branches.forEach(branch -> {
						pp.endl().append(branch);
					});
				});
			});
		}
		}
	}
}
