package zlk.clcalc;

import java.util.List;

import zlk.common.ConstValue;
import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * クロージャの生成と複数変数の関数呼び出しを正規化した式
 */
public sealed interface CcExp extends PrettyPrintable, LocationHolder {

	record CcCnst(
			ConstValue value,
			Location loc) implements CcExp {
		Type type() {
			return value.type();
		}
	}

	record CcVar(  // 変数の参照
			Id id,
			Location loc) implements CcExp {}

	record CcDirectApp(  // invokeになる部分
			Id funId,
			List<CcExp> args,
			Location loc) implements CcExp {}

	record CcClosureApp(  // Function.applyになる部分
			CcExp funExp,
			List<CcExp> args,
			Location loc) implements CcExp {}

	record CcMkCls(
			Id implId,  // メソッド定義
			List<CcExp> caps,  // キャプチャする式
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
			Type targetTy,
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
		case CcDirectApp(Id funid, List<CcExp> args, Location loc) -> {
			yield new CcDirectApp(
					map.getOrDefault(funid, funid),
					args.stream().map(arg -> arg.substId(map)).toList(),
					loc);
		}
		case CcClosureApp(CcExp funExp, List<CcExp> args, Location loc) -> {
			yield new CcClosureApp(
					funExp.substId(map),
					args.stream().map(arg -> arg.substId(map)).toList(),
					loc);
		}
		case CcMkCls(Id clsFunc, List<CcExp> caps, Location loc) -> {
			yield new CcMkCls(
					map.getOrDefault(clsFunc, clsFunc),
					caps.stream().map(cap -> cap.substId(map)).toList(),
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
		case CcCase(CcExp target, Type targetTy, List<CcCaseBranch> branches, Location loc) -> {
			yield new CcCase(
					target.substId(map),
					targetTy,
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
		case CcDirectApp(Id funId, List<CcExp> args, Location _) -> {
			pp.append("directApp:").endl();
			pp.indent(() -> {
				pp.append("funId: ").append(funId).endl();
				pp.append("argExp:");
				pp.indent(() -> {
					args.forEach(arg -> {
						pp.endl().append(arg);
					});
				});
			});
		}
		case CcClosureApp(CcExp funExp, List<CcExp> args, Location _) -> {
			pp.append("closureApp:").endl();
			pp.indent(() -> {
				pp.append("funExp:").endl();
				pp.indent(() -> {
					pp.append(funExp).endl();
				});
				pp.append("argExp:");
				pp.indent(() -> {
					args.forEach(arg -> {
						pp.endl().append(arg);
					});
				});
			});
		}
		case CcMkCls(Id clsFunc, List<CcExp> caps, Location _) -> {
			pp.append("mkCls:").endl();
			pp.indent(() -> {
				pp.append("clsFunc: ").append(clsFunc).endl();
				pp.append("argExp:");
				pp.indent(() -> {
					caps.forEach(cap -> {
						pp.endl().append(cap);
					});
				});			});
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
		case CcCase(CcExp target, Type _, List<CcCaseBranch> branches, Location _) -> {
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
