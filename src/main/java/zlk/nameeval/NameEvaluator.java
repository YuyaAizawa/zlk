package zlk.nameeval;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import zlk.ast.AnType;
import zlk.ast.CaseBranch;
import zlk.ast.Constructor;
import zlk.ast.Decl.TypeDecl;
import zlk.ast.Decl.ValDecl;
import zlk.ast.Exp;
import zlk.ast.Exp.App;
import zlk.ast.Exp.Case;
import zlk.ast.Exp.Cnst;
import zlk.ast.Exp.If;
import zlk.ast.Exp.Lamb;
import zlk.ast.Exp.Let;
import zlk.ast.Exp.Var;
import zlk.ast.Module;
import zlk.ast.Pattern;
import zlk.common.ConstValue;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.core.Builtin;
import zlk.idcalc.IcCaseBranch;
import zlk.idcalc.IcCtor;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcExp.IcApp;
import zlk.idcalc.IcExp.IcCase;
import zlk.idcalc.IcExp.IcCnst;
import zlk.idcalc.IcExp.IcIf;
import zlk.idcalc.IcExp.IcLamb;
import zlk.idcalc.IcExp.IcLet;
import zlk.idcalc.IcExp.IcVarCtor;
import zlk.idcalc.IcExp.IcVarForeign;
import zlk.idcalc.IcExp.IcVarLocal;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcPattern;
import zlk.idcalc.IcTypeDecl;
import zlk.idcalc.IcValDecl;
import zlk.util.Location;

public final class NameEvaluator {

	private final Module module;
	private final Env env;
	private final IdMap<Builtin> builtins;
	private final IdMap<Type> types;
	private final IdMap<Type> ctors;
	private final IdList sccDeclsInModule;
	private final IdToIds sccRef;

	public NameEvaluator(Module module) {
		this.module = module;
		this.builtins = Builtin.functions().stream().collect(IdMap.collector(b -> b.id(), b -> b));
		this.types = new IdMap<>();
		this.ctors = new IdMap<>();
		this.sccDeclsInModule = new IdList();
		this.sccRef = new IdToIds();

		env = new Env();
		Type.BUILTIN.stream().forEach(ty -> {
			try {
				types.put(env.registerGlobal(ty.ctor()), ty);
			} catch (DuplicatedNameException e) {
				throw new Error("builtin type dupicated", e);
			}
		});
		builtins.forEach((_, b) -> {
			try {
				env.registerGlobal(b.id());
			} catch (DuplicatedNameException e) {
				throw new Error("builtin value dupicated", e);
			}
		});
	}

	public IcModule eval() {
		env.pushScope(module.name());

		// resister types
		module.decls().forEach(def -> {
			switch(def) {
			case TypeDecl(String name, List<AnType.Var>args, _, _) -> {
				Id id;
				try {
					id = env.register(name);
				} catch (DuplicatedNameException e) {
					// TODO コンパイルメッセージに追加
					throw new RuntimeException(e);
				}
				List<Type> tyArgs = args.stream().map(this::eval).toList();  // TODO: コンストラクタが入っていないことを確認
				Type type = new Type.CtorApp(id, tyArgs);
				types.put(id, type);
			}
			default -> {}
			}
		});

		// resister toplevels
		module.decls().forEach(def -> {
			switch(def) {
			case TypeDecl(String name, _, List<Constructor> ctors, _) -> {
				// 2 step registrations for mutual recursion types
				Type retTy = types.get(env.get(name));
				ctors.forEach(ctor -> {
					Id ctorId;
					try {
						ctorId = env.register(ctor.name());
					} catch (DuplicatedNameException e) {
						// TODO コンパイルメッセージに追加
						throw new RuntimeException(e);
					}
					// TODO: 宣言された型とコンストラクタの型の整合性検査
					Type type =Type.arrow(
							ctor.args().stream().map(ty -> eval(ty)).toList(),
							retTy
					);
					this.ctors.put(ctorId, type);
				});
			}
			case ValDecl(String name, _, _, _, _) -> {
				try {
					Id id = env.register(name);
					sccDeclsInModule.add(id);
				} catch (DuplicatedNameException e) {
					// TODO コンパイルメッセージに追加
					throw new RuntimeException(e);
				}
			}}
		});

		List<IcTypeDecl> icTypes = new ArrayList<>();
		List<IcValDecl> icDecls = new ArrayList<>();

		module.decls().forEach(def -> {
			switch(def) {
			case TypeDecl ty -> icTypes.add(eval(ty));
			case ValDecl fun -> icDecls.add(eval(fun));
			}
		});

		env.popScope();
		if(env.scoped.size() != 0) {
			throw new AssertionError();
		}
		return new IcModule(module.name(), icTypes, icDecls, module.origin());
	}

	public IcTypeDecl eval(TypeDecl union) {
		Id id = env.get(union.name());

		List<Type> vars = union.vars().stream().map(var -> eval(var)).toList();

		List<IcCtor> ctors = union.ctors().stream().map(ctor -> {
			Id ctorId = env.get(ctor.name());
			List<Type> args = ctor.args().stream().map(ty -> eval(ty)).toList();
			return new IcCtor(ctorId, args, ctor.loc());
		}).toList();

		return new IcTypeDecl(id, vars, ctors, union.loc());
	}

	public IcValDecl eval(ValDecl decl) {
		try {
			String declName = decl.name();
			env.pushScope(declName);

			Id id = env.get(declName);
			Optional<Type> anno = decl.anno().map(a -> eval(a));
			List<IcPattern> args = decl.args().stream().map(a -> eval(a)).toList();
			IcExp body = eval(decl.body(), id);

			env.popScope();

			return new IcValDecl(id, anno, args, body, decl.loc());
		} catch(RuntimeException e) {
			throw new RuntimeException("in "+decl.name(), e);
		}
	}

	private IcExp eval(Exp exp, Id scope) {
		switch(exp) {
		case Cnst(ConstValue value, Location loc):
			return new IcCnst(value, loc);
		case Var(String name, Location loc): {
			Id id = env.get(name);

			Builtin builtin = builtins.getOrNull(id);
			if(builtin != null) {
				return new IcVarForeign(id, builtin.type(), loc);
			}

			Type ctor = ctors.getOrNull(id);
			if(ctor != null) {
				return new IcVarCtor(id, ctor, loc);
			}
			if(sccDeclsInModule.contains(id)) {
				sccRef.add(scope, id);
			}

			return new IcVarLocal(id, loc);
		}
		case Lamb(List<Pattern> patterns, Exp body, Location loc): {
			List<IcPattern> args = patterns.stream().map(a -> eval(a)).toList();
			IcExp body_ = eval(body, scope);
			return new IcLamb(args, body_, loc);
		}
		case App(List<Exp> exps, Location loc): {
			IcExp fun = eval(exps.get(0), scope);
			List<IcExp> args = exps.subList(1, exps.size()).stream()
					.map(arg -> eval(arg, scope))
					.toList();
			return new IcApp(fun, args, loc);
		}
		case If(Exp cond, Exp exp1, Exp exp2, Location loc):
			return new IcIf(eval(cond, scope), eval(exp1, scope), eval(exp2, scope), loc);
		case Let(List<ValDecl> decls, Exp body, Location loc): {
			if(decls.isEmpty()) {
				return eval(body, scope);
			} else {
				for(ValDecl decl: decls) {
					try {
						Id id = env.register(decl.name());
						sccDeclsInModule.add(id);
					} catch (DuplicatedNameException e) {
						// TODO コンパイルメッセージに追加
						throw new RuntimeException(e);
					}
				}
				return new IcLet(
						decls.stream().map(decl -> eval(decl)).toList(),
						eval(body, scope),
						loc
				);
			}
		}
		case Case(Exp exp_, List<CaseBranch> branches, Location loc): {
			IcExp target = eval(exp_, scope);
			List<IcCaseBranch> branches_ = new ArrayList<>();
			for (int i = 0; i < branches.size(); i++) {
				branches_.add(eval(branches.get(i), i, scope));
			}
			return new IcCase(target, branches_, loc);
		}
		}
	}

	private IcCaseBranch eval(CaseBranch branch, int branchIdx, Id scope) {
		env.pushScope("_" + branchIdx);
		IcPattern pat = eval(branch.pattern());
		IcExp body = eval(branch.body(), scope);
		env.popScope();
		return new IcCaseBranch(pat, body, branch.loc());
	}

	private IcPattern eval(Pattern pat) {
		switch(pat) {
		case Pattern.Var(String name, Location loc): {
			Id id;
			try {
				id = env.register(name);
			} catch (DuplicatedNameException e) {
				// TODO コンパイルメッセージに追加
				throw new RuntimeException(e);
			}
			return new IcPattern.Var(id, loc);
		}
		case Pattern.Ctor(String name, List<Pattern> args, Location loc): {
			Id ctor = env.get(name);
			List<Type> argTys = ctors.get(ctor).flatten();
			List<IcPattern.Arg> args_ = new ArrayList<>();
			for (int i = 0; i < args.size(); i++) {
				args_.add(new IcPattern.Arg(eval(args.get(i)), argTys.get(i)));
			}
			IcVarCtor icVarCtor = new IcVarCtor(ctor, ctors.get(ctor), Location.noLocation());  // TODO location
			return new IcPattern.Ctor(icVarCtor, args_, loc);
		}
		}
	}

	private Type eval(AnType aTy) {
		return switch (aTy) {
		case AnType.Unit _ -> Type.UNIT;
		case AnType.Var(String name, _) -> new Type.Var(name);
		case AnType.Type(String ctor, List<AnType> args, _) -> {
			Id ctor_ = env.get(ctor);
			List<Type> args_ = args.stream().map(arg -> eval(arg)).toList();
			yield new Type.CtorApp(ctor_, args_);
		}
		case AnType.Arrow(AnType arg, AnType ret, _) -> new Type.Arrow(eval(arg), eval(ret));
		};
	}
}
