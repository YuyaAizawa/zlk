package zlk.jvmcodegen;

import static zlk.util.ErrorUtils.neverHappen;
import static zlk.util.ErrorUtils.todo;

import java.util.ArrayList;
import java.util.List;

import zlk.clcalc.CcDecl;
import zlk.clcalc.CcExp;
import zlk.clcalc.CcModule;
import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.common.type.TyArrow;
import zlk.common.type.Type;
import zlk.jvmcode.ClassDecl;
import zlk.jvmcode.JtCnst;
import zlk.jvmcode.JtExp;
import zlk.jvmcode.JtInDy;
import zlk.jvmcode.JtInItf;
import zlk.jvmcode.JtInSt;
import zlk.jvmcode.MethodDecl;
import zlk.jvmcode.MethodInfo;
import zlk.jvmcode.type.JtType;
import zlk.jvmcode.type.Obj;
import zlk.jvmcode.type.Prim;
import zlk.util.Location;

public final class JvmCodeGenerator {

	private static final JtType OBJECT =
			new Obj("java/lang/Object");

	private static final MethodInfo APPLY =
			new MethodInfo(
					"java/util/function/Function",
					"apply",
					List.of(OBJECT),
					OBJECT);

	private static final JtType FUNCTION =
			new Obj("java/util/function/Function");

	private final String moduleName;
	private final String origin;
	private final IdMap<Type> type;
	private final IdMap<MethodInfo> toplevels;

	private JvmCodeGenerator(CcModule module) {
		moduleName = module.name();
		origin = module.origin();
		type = module.type();

		toplevels = module.toplevels()
				.stream()
				.collect(IdMap.collector(
						decl -> decl.id(),
						decl -> toMethodInfo(moduleName, decl.id(), type)));
	}

	public static ClassDecl compile(CcModule module) {
		JvmCodeGenerator instance = new JvmCodeGenerator(module);

		List<MethodDecl> methods =
				module.toplevels()
						.stream()
						.map(instance::compile)
						.toList();

		return new ClassDecl(module.name(), methods);
	}

	private MethodDecl compile(CcDecl decl) {
		IdList args = decl.args();
		List<String> argNames = new ArrayList<>(args.size());
		List<JtType> argTypes = new ArrayList<>(args.size());
		for(Id arg : args) {
			argNames.add(arg.simpleName());
			argTypes.add(toBinaryType(type.get(arg)));
		}
		JtType retType = toBinaryType(type.get(decl.id()).asArrow().ret());
		JtExp body = compile(decl.body());

		return new MethodDecl(decl.id().simpleName(), argTypes, argNames, retType, body, decl.loc());
	}

	private static JtType toBinaryType(Type ty) {
		return ty.fold(
				unit  -> todo(),
				bool  -> Prim.Z,
				i32   -> Prim.I,
				arrow -> todo());
	}

	private JtExp compile(CcExp exp) {
		Location loc = exp.loc();
		return exp.fold(
				cnst  -> {
					return cnst.value().fold(
							bool -> (JtExp)JtCnst.i(bool.value() ? 1 : 0, loc),
							i32  -> (JtExp)JtCnst.i(i32.value(), loc));
				},
				var   -> todo(),
				call  -> {
					List<JtExp> args = call.args().stream().map(this::compile).toList();
					return call.fun().fold(
							cnst_ -> neverHappen(),
							var_  -> {
								MethodInfo method = toplevels.getOrNull(var_.id());
								if(method != null) {
									// トップレベルメソッドに対する呼び出し
									return new JtInSt(method, args, loc);
								} else {
									// 関数オブジェクトに対する呼び出し
									return new JtInItf(APPLY, compile(var_), args, loc);
								}
							},
							call_  -> {
								// 関数オブジェクトに対する呼び出し
								return new JtInItf(APPLY, compile(call_), args, loc);
							},
							mkCls_ -> {
								// 関数オブジェクトに対する呼び出し
								return new JtInItf(APPLY, compile(mkCls_), args, loc);
							},
							if__   -> {
								// 関数オブジェクトに対する呼び出し
								return new JtInItf(APPLY, compile(if__), args, loc);
							},
							let_   -> {
								// 関数オブジェクトに対する呼び出し
								return new JtInItf(APPLY, compile(let_), args, loc);
							});
					},
				mkCls -> {
					Id impl = mkCls.clsFunc();
					IdList caps = mkCls.caps();
					List<JtType> indyArgTys = caps.stream().map(cap -> toBinaryType(type.get(cap))).toList();
					TyArrow indyRetTy = types.get(impl).apply(caps.size()).asArrow();
					if(indyRetTy == null) {
						throw new Error(mkCls.toString());
					}

					return new JtInDy(
							moduleName,
							toMethodName(mkCls.id()),
							indyArgTys,
							FUNCTION,
							"java/util/function/Function",
							"apply"
							);
				},
				if_   -> todo(),
				let   -> todo());
	}

	private String toMethodName(Id id) {
		return id.canonicalName().substring(moduleName.length()+1).replaceAll("\\.", "$");
	}

	private MethodInfo toMethodInfo(String moduleName, Id id, IdMap<Type> type) {
		String owner = moduleName;
		String name = toMethodName(id);
		List<Type> typeList = type.get(id).flatten();
		int len = typeList.size();
		List<JtType> args = typeList.subList(0, len-1)
				.stream()
				.map(JvmCodeGenerator::toBinaryType)
				.toList();
		JtType ret = toBinaryType(typeList.get(len-1));
		return new MethodInfo(owner, name, args, ret);
	}
}
