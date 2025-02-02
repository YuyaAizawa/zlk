package zlk.clconv;

import static zlk.util.ErrorUtils.neverHappen;
import static zlk.util.ErrorUtils.todo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import zlk.clcalc.CcApp;
import zlk.clcalc.CcCase;
import zlk.clcalc.CcCaseBranch;
import zlk.clcalc.CcCnst;
import zlk.clcalc.CcCtor;
import zlk.clcalc.CcExp;
import zlk.clcalc.CcFunc;
import zlk.clcalc.CcIf;
import zlk.clcalc.CcLet;
import zlk.clcalc.CcMkCls;
import zlk.clcalc.CcModule;
import zlk.clcalc.CcType;
import zlk.clcalc.CcVar;
import zlk.common.ConstValue;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.idcalc.IcCaseBranch;
import zlk.idcalc.IcCtor;
import zlk.idcalc.IcFunDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcExp.IcAbs;
import zlk.idcalc.IcExp.IcApp;
import zlk.idcalc.IcExp.IcCase;
import zlk.idcalc.IcExp.IcCnst;
import zlk.idcalc.IcExp.IcIf;
import zlk.idcalc.IcExp.IcLet;
import zlk.idcalc.IcExp.IcLetrec;
import zlk.idcalc.IcExp.IcVarCtor;
import zlk.idcalc.IcExp.IcVarForeign;
import zlk.idcalc.IcExp.IcVarLocal;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcPattern;
import zlk.idcalc.IcPattern.Var;
import zlk.idcalc.IcTypeDecl;
import zlk.util.Location;

public final class ClosureConveter {

	private final IcModule src;
	private final IdMap<Type> type; // 変換後のIdの型を追加
	private final Set<Id> knowns;

	private final List<CcFunc> toplevels;
	private final AtomicInteger closureCount;

	public ClosureConveter(IcModule src, IdMap<Type> type, IdList builtins) {
		this.src = src;
		this.type = type;
		this.knowns = new HashSet<>();
		src.decls().forEach(decl -> knowns.add(decl.id()));
		src.types().forEach(union -> union.ctors().forEach(ctor -> knowns.add(ctor.id())));
		knowns.addAll(builtins);
		this.toplevels = new ArrayList<>();
		this.closureCount = new AtomicInteger();
	}

	public CcModule convert() {
		src.decls()
				.stream()
				.map(decl -> compileFunc(decl.id(), decl.args(), decl.body()))
				.forEach(maybeCls -> maybeCls.ifPresent(cls -> {
					throw new RuntimeException("toplevel must not be closure: "+cls.id()); }));

		List<CcType> types = src.types().stream().map(ty -> convert(ty)).toList();
		return new CcModule(src.name(), types, toplevels, src.origin());
	}


	/**
	 * 関数をトップレベルに変換し追加する．クロージャに変換された場合，その作成を返す．
	 * @return クロージャの生成（クロージャが必要だったとき）
	 */
	private Optional<CcMkCls> compileFunc(Id id, List<IcPattern> args_, IcExp body) {

		knowns.add(id);
		CcExp ccBody = compile(body);
		IdList frees = fvFunc(ccBody, args_);

		if(frees.isEmpty()) {
			System.out.println(id + " is not cloeure.");
			toplevels.add(new CcFunc(id, args_, ccBody, body.loc()));
			return Optional.empty();

		} else {
			System.out.println(id + " is cloeure. frees: "+frees);
			CcFunc closureFunc = makeClosure(id, frees, args_, ccBody, type.get(id).apply(args_.size()), body.loc());
			toplevels.add(closureFunc);
			knowns.add(closureFunc.id());
			return Optional.of(new CcMkCls(closureFunc.id(), frees, closureFunc.loc()));
		}
	}

	/**
	 * 自由変数を引数に入れた新しい関数宣言を作る
	 * @param original 元の関数のId
	 * @param frees 自由変数
	 * @param args_ 元の関数の引数
	 * @param body 元の関数の中身
	 * @param retTy 元の関数の戻り値型
	 * @param loc 元の関数のLocation
	 * @return
	 */
	private CcFunc makeClosure(Id original, IdList frees, List<IcPattern> args_, CcExp body, Type retTy, Location loc) {
		List<Type> types = new ArrayList<>();
		frees.forEach(id -> types.add(type.get(id)));
		args_.forEach(pat -> types.add(type.get(pat.headId())));
		types.add(retTy);

		Type clsTy = types.get(0).toTree(types.subList(1, types.size()));
		Id clsId = freshId(original);
		type.put(clsId, clsTy);

		IdMap<Id> idMap =
				frees.stream().collect(IdMap.collector(
						Function.identity(),
						id -> Id.fromParentAndSimpleName(clsId, id.simpleName())));
		List<IcPattern> clsArgs = new ArrayList<>(args_);
		for(Id free : frees) {
			Id newId = idMap.get(free);
			clsArgs.add(new Var(newId, Location.noLocation()));
			type.put(newId, type.get(free));
		}

		CcExp clsBody = body.substId(idMap);

		return new CcFunc(clsId, clsArgs, clsBody, loc);
	}

	private CcExp compile(IcExp exp) {
		return switch (exp) {
		case IcCnst(ConstValue value, Location loc) -> {
			yield new CcCnst(value, loc);
		}
		case IcVarLocal(Id id, Location loc) -> {
			yield new CcVar(id, loc);
		}
		case IcVarForeign(Id id, Type _, Location loc) -> {
			yield new CcVar(id, loc);
		}
		case IcVarCtor(Id id, Type _, Location loc) -> {
			yield new CcVar(id, loc);
		}
		case IcAbs(Id _, Type _, IcExp body, Location _) -> {
			yield neverHappen("no anonymous abs in this version.", body.loc());
		}
		case IcApp(IcExp fun, List<IcExp> args, Location loc) -> {
			CcExp funExp = compile(fun);
			List<CcExp> argExps = args.stream()
					.map(arg -> compile(arg))
					.toList();
			yield new CcApp(funExp, argExps, loc);
		}
		case IcIf(IcExp cond, IcExp thenExp, IcExp elseExp, Location loc) -> {
			yield new CcIf(
					compile(cond),
					compile(thenExp),
					compile(elseExp),
					loc);
		}
		case IcLet(IcFunDecl decl, IcExp body, Location loc) -> {
			Id id = decl.id();
			List<IcPattern> args = decl.args();
			IcExp bounded = decl.body();
			CcExp letBody = compile(body);

			if(!args.isEmpty()) {
				yield compileFunc(id, decl.args(), bounded)
						.map(mkCls -> (CcExp)new CcLet(id, mkCls, letBody, loc))
						.orElse(letBody); // トップレベルで定義されているのでletは要らない
			} else {
				yield new CcLet(id, compile(bounded), letBody, loc);
			}
		}
		case IcLetrec(List<IcFunDecl> decls, IcExp body, Location loc) -> {
			if(decls.size() != 1) {
				todo();
			}
			IcFunDecl decl = decls.get(0);
			Id id = decl.id();
			List<IcPattern> args = decl.args();
			IcExp bounded = decl.body();
			CcExp letBody = compile(body);

			if(!args.isEmpty()) {
				yield compileFunc(id, decl.args(), bounded)
						.map(mkCls -> (CcExp)new CcLet(id, mkCls, letBody, loc))
						.orElse(letBody);
			} else {
				yield new CcLet(id, compile(bounded), letBody, loc);
			}
		}
		case IcCase(IcExp target, List<IcCaseBranch> branches, Location loc) -> {
			CcExp compiledTarget = compile(target);
			List<CcCaseBranch> compiledBranches =
					branches.stream()
							.map(branch -> new CcCaseBranch(
									branch.pattern(),
									compile(branch.body()),
									branch.loc()))
							.toList();
			yield new CcCase(compiledTarget, compiledBranches, loc);
		}
		};
	}

	private IdList fvFunc(CcExp body, List<IcPattern> args) {
		IdList free = new IdList();
		Set<Id> bounded = new HashSet<>(knowns);
		args.forEach(arg -> arg.accumulateVars(bounded));
		fv(body, bounded, free);
		return free;
	}

	private void fv(CcExp exp, Set<Id> bounded, IdList free) {
		exp.match(
				_ -> {},
				var  -> {
					Id id = var.id();
					if(!bounded.contains(id) && !free.contains(id)) {
						free.add(id);
					}
				},
				call  -> {
					fv(call.fun(), bounded, free);
					call.args().forEach(arg -> fv(arg, bounded, free));
				},
				mkCls -> {
					if(!bounded.contains(mkCls.clsFunc())) {
						throw new AssertionError();
					}
					mkCls.caps().stream()
							.filter(id -> !bounded.contains(id) && !free.contains(id))
							.forEach(free::add);
				},
				if_  -> {
					fv(if_.cond(), bounded, free);
					fv(if_.thenExp(), bounded, free);
					fv(if_.elseExp(), bounded, free);
				},
				let  -> {
					bounded.add(let.var());
					fv(let.boundExp(), bounded, free);
					fv(let.body(), bounded, free);
				},
				case_ -> {
					fv(case_.target(), bounded, free);
					for(CcCaseBranch branch: case_.branches()) {
						branch.pattern().accumulateVars(bounded);
						fv(branch.body(), bounded, free);
					}
				});
	}

	private Id freshId(Id original) {
		String orgStr = original.canonicalName();

		int idx = orgStr.indexOf('.');
		if(idx == -1) {
			throw new Error(orgStr);
		}
		String exceptModule = orgStr.substring(idx+1);

		return Id.fromCanonicalName(
				src.name()
				+ Id.SEPARATOR + closureCount.getAndIncrement()
				+ Id.SEPARATOR + exceptModule);
	}

	private CcType convert(IcTypeDecl icType) {
		return new CcType(icType.id(), icType.ctors().stream().map(ctor -> convert(ctor)).toList(), icType.loc());
	}

	private CcCtor convert(IcCtor icCtor) {
		return new CcCtor(icCtor.id(), icCtor.args(), icCtor.loc());
	}
}
