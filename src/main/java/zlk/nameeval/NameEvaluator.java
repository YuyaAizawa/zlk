package zlk.nameeval;

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
import zlk.common.Location;
import zlk.common.Type;
import zlk.common.id.Id;
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
import zlk.idcalc.IcPattern.Arg;
import zlk.idcalc.IcTypeDecl;
import zlk.idcalc.IcValDecl;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

public final class NameEvaluator {

	private final Module module;
	private final Env env;
	private final IdMap<Builtin> builtins;
	private final IdMap<Type> types;
	private final IdMap<Type> ctors;

	public NameEvaluator(Module module) {
		this.module = module;
		this.builtins = Builtin.functions().fold(IdMap.folder(b -> b.id(), b -> b));
		this.types = new IdMap<>();
		this.ctors = new IdMap<>();

		env = new Env();
		Type.BUILTIN.forEach(ty -> {
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
			case TypeDecl(String name, Seq<AnType.Var>args, _, _) -> {
				Id id;
				try {
					id = env.register(name);
				} catch (DuplicatedNameException e) {
					// TODO コンパイルメッセージに追加
					throw new RuntimeException(e);
				}
				Seq<Type> tyArgs = args.map(this::eval);
				Type type = new Type.CtorApp(id, tyArgs);
				types.put(id, type);
			}
			default -> {}
			}
		});

		// resister toplevels
		module.decls().forEach(def -> {
			switch(def) {
			case TypeDecl(String name, _, Seq<Constructor> ctors, _) -> {
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
					Type type = Type.fromSeq(Seq.concat(
							ctor.args().map(ty -> eval(ty)),
							Seq.of(retTy)));
					this.ctors.put(ctorId, type);
				});
			}
			case ValDecl(String name, _, _, _, _) -> {
				try {
					env.register(name);
				} catch (DuplicatedNameException e) {
					// TODO コンパイルメッセージに追加
					throw new RuntimeException(e);
				}
			}}
		});

		SeqBuffer<IcTypeDecl> icTypes = new SeqBuffer<>();
		SeqBuffer<IcValDecl> icDecls = new SeqBuffer<>();

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
		return new IcModule(module.name(), icTypes.toSeq(), icDecls.toSeq());
	}

	public IcTypeDecl eval(TypeDecl union) {
		Id id = env.get(union.name());

		Seq<Type> vars = union.vars().map(var -> eval(var));

		Seq<IcCtor> ctors = union.ctors().map(ctor -> {
			Id ctorId = env.get(ctor.name());
			Seq<Type> args = ctor.args().map(ty -> eval(ty));
			return new IcCtor(ctorId, args, ctor.loc());
		});

		return new IcTypeDecl(id, vars, ctors, union.loc());
	}

	public IcValDecl eval(ValDecl decl) {
		try {
			String declName = decl.name();
			env.pushScope(declName);

			Id id = env.get(declName);
			Optional<Type> anno = decl.anno().map(a -> eval(a));
			Seq<IcPattern> args = decl.args().map(a -> eval(a));
			IcExp body = eval(decl.body(), id);

			env.popScope();

			return new IcValDecl(id, anno, args, body, decl.loc());
		} catch(RuntimeException e) {
			throw new RuntimeException("in "+decl.name(), e);
		}
	}

	private IcExp eval(Exp exp, Id scope) {
		return switch(exp) {
		case Cnst(ConstValue value, Location loc) ->
			new IcCnst(value, loc);

		case Var(String name, Location loc) -> {
			Id id = env.get(name);

			Builtin builtin = builtins.getOrNull(id);
			if(builtin != null) {
				yield new IcVarForeign(id, builtin.type(), loc);
			}

			Type ctor = ctors.getOrNull(id);
			if(ctor != null) {
				yield new IcVarCtor(id, ctor, loc);
			}

			yield new IcVarLocal(id, loc);
		}

		case Lamb(Seq<Pattern> patterns, Exp body, Location loc) ->
			new IcLamb(
					patterns.map(a -> eval(a)),
					eval(body, scope),
					loc);

		case App(Seq<Exp> exps, Location loc) ->
			new IcApp(
					eval(exps.head(), scope),
					exps.tail().map(arg -> eval(arg, scope)),
					loc);

		case If(Exp cond, Exp exp1, Exp exp2, Location loc) ->
			new IcIf(
					eval(cond, scope),
					eval(exp1, scope),
					eval(exp2, scope),
					loc);

		case Let(Seq<ValDecl> decls, Exp body, Location loc) -> {
			if(decls.isEmpty()) {
				yield eval(body, scope);
			} else {
				for(ValDecl decl: decls) {
					try {
						env.register(decl.name());
					} catch (DuplicatedNameException e) {
						// TODO コンパイルメッセージに追加
						throw new RuntimeException(e);
					}
				}
				yield new IcLet(
						decls.map(decl -> eval(decl)),
						eval(body, scope),
						loc);
			}
		}

		case Case(Exp exp_, Seq<CaseBranch> branches, Location loc) ->
			new IcCase(
					eval(exp_, scope),
					branches.mapIndexed((i, branch) -> eval(branch, i, scope)),
					loc);
		};
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
		case Pattern.Ctor(String name, Seq<Pattern> args, Location loc): {
			Id ctor = env.get(name);
			IcVarCtor icVarCtor = new IcVarCtor(ctor, ctors.get(ctor), Location.noLocation());  // TODO location
			Seq<Type> argTys = ctors.get(ctor).flatten();
			Seq<Arg> dectorArgs = args.mapIndexed((i, arg) -> new IcPattern.Arg(eval(arg), argTys.at(i)));
			return new IcPattern.Dector(icVarCtor, dectorArgs, loc);
		}
		}
	}

	private Type eval(AnType aTy) {
		return switch (aTy) {
		case AnType.Unit _ -> Type.UNIT;
		case AnType.Var(String name, _) -> new Type.Var(name);
		case AnType.Type(String ctor, Seq<AnType> args, _) ->
			new Type.CtorApp(env.get(ctor), args.map(arg -> eval(arg)));
		case AnType.Arrow(AnType arg, AnType ret, _) -> new Type.Arrow(eval(arg), eval(ret));
		};
	}
}
