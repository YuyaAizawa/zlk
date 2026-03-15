package zlk.recon.constraint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import zlk.common.Type;
import zlk.common.id.Id;
import zlk.recon.FreshFlex;
import zlk.recon.Variable;
import zlk.recon.constraint.RcType.AppN;
import zlk.recon.constraint.RcType.FunN;
import zlk.recon.constraint.RcType.VarN;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;


public sealed interface RcType extends PrettyPrintable
permits VarN, AppN, FunN {
	record VarN(Variable var) implements RcType {}
	record AppN(Id id, List<RcType> args) implements RcType {}
	record FunN(RcType arg, RcType ret) implements RcType {}

	public static final RcType BOOL = new AppN(Type.BOOL.ctor(), List.of());
	public static final RcType I32  = new AppN(Type.I32.ctor() , List.of());

	/**
	 * 注釈用の型から生成された制約用の型情報
	 *
	 * @param flex 制約用の型に含まれる型変数
	 * @param argTys 関数型だった場合の引数型をflattenしたもの
	 * @param retTy 関数型だった場合の戻り値の型かその他の場合の型そのもの
	 */
	public record FromType(List<Variable> flexes, List<RcType> argTys, RcType resultTy) {}

	/**
	 * 注釈などに使う型から制約用の型を生成する
	 * @param ty
	 * @return
	 */
	public static FromType from(Type ty, FreshFlex freshFlex) {
		Map<String, Variable> vars = new HashMap<>();

		Function<Type, RcType> conv = new Function<>() {
			@Override
			public RcType apply(Type t) {
				return switch(t) {
				case Type.Var(String name) ->
					new VarN(vars.computeIfAbsent(name, _ -> freshFlex.getVariable()));
				case Type.CtorApp(Id id, List<Type> args) ->
					new AppN(id, args.stream().map(this).toList());
				case Type.Arrow(Type arg, Type ret) ->
					new FunN(apply(arg), apply(ret));
				};
			}
		};

		RcType resultTy = conv.apply(ty);
		List<RcType> argTys = new java.util.ArrayList<>();
		while (resultTy instanceof RcType.FunN(RcType arg, RcType ret)) {
			argTys.add(arg);
			resultTy = ret;
		}

		List<Variable> flexes = new ArrayList<>(vars.values());  // 参照リーク防止

		return new FromType(flexes, argTys, resultTy);
	}

	default List<RcType> flatten() {
		List<RcType> flatten = new ArrayList<>();

		RcType ret = this;
		while(ret instanceof FunN fun) {
			flatten.add(fun.arg());
			ret = fun.ret();
		}
		flatten.add(ret);
		return flatten;
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case VarN(Variable var) -> {
			pp.append(var);
		}
		case AppN(Id id, List<RcType> args) -> {
			pp.append(id);
			for(RcType arg : args) {
				pp.append(" ");
				switch(arg) {
				case VarN _ ->
					pp.append(arg);
				case AppN(Id _, List<RcType> args_) when args_.isEmpty() ->
					pp.append(arg);
				default ->
					pp.append("(").append(arg).append(")");
				}
			}
		}
		case FunN(RcType arg, RcType ret) -> {
			switch(arg) {
			case FunN _ -> pp.append("(").append(arg).append(")");
			default -> pp.append(arg);
			}
			pp.append(" -> ");
			pp.append(ret);
		}
		}
	}
}

