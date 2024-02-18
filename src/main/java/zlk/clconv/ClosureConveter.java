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
import java.util.stream.Stream;

import zlk.clcalc.CcApp;
import zlk.clcalc.CcCase;
import zlk.clcalc.CcCaseBranch;
import zlk.clcalc.CcCnst;
import zlk.clcalc.CcCtor;
import zlk.clcalc.CcDecl;
import zlk.clcalc.CcExp;
import zlk.clcalc.CcIf;
import zlk.clcalc.CcLet;
import zlk.clcalc.CcMkCls;
import zlk.clcalc.CcModule;
import zlk.clcalc.CcType;
import zlk.clcalc.CcVar;
import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.common.type.Type;
import zlk.idcalc.IcCtor;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcPVar;
import zlk.idcalc.IcPattern;
import zlk.idcalc.IcType;
import zlk.util.Location;

public final class ClosureConveter {

	private final IcModule src;
	private final IdMap<Type> type; // 変換後のIdの型を追加
	private final Set<Id> knowns;

	private final List<CcDecl> toplevels;
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
			toplevels.add(new CcDecl(id, args_, ccBody, body.loc()));
			return Optional.empty();

		} else {
			System.out.println(id + " is cloeure. frees: "+frees);
			CcDecl closureFunc = makeClosure(id, frees, args_, ccBody, type.get(id).apply(args_.size()), body.loc());
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
	private CcDecl makeClosure(Id original, IdList frees, List<IcPattern> args_, CcExp body, Type retTy, Location loc) {
		List<Type> types =
				Stream.concat(Stream.concat(
						frees.stream().map(type::get),
						args_.stream()
								.map(pat -> pat.fold(
										var -> type.get(var.id()),
										ctor -> type.get(ctor.ctor().id())))),
						Stream.of(retTy))
						.toList();
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
			clsArgs.add(new IcPVar(newId, Location.noLocation()));
			type.put(newId, type.get(free));
		}

		CcExp clsBody = body.substId(idMap);

		return new CcDecl(clsId, clsArgs, clsBody, loc);
	}

	private CcExp compile(IcExp body) {
		return body.fold(
				cnst    -> new CcCnst(cnst.value(), cnst.loc()),
				var     -> new CcVar(var.id(), var.loc()),
				foreign -> new CcVar(foreign.id(), foreign.loc()),
				ctor    -> new CcVar(ctor.id(), ctor.loc()),
				abs     -> {
					return neverHappen(
							"no anonymous abs in this version.", body.loc());
				},
				app     -> {
					CcExp funExp = compile(app.fun());
					List<CcExp> argExps =
							app.args().stream()
							.map(arg -> compile(arg)).toList();
					return new CcApp(funExp, argExps, app.loc());
				},
				if_     -> new CcIf(
						compile(if_.cond()),
						compile(if_.exp1()),
						compile(if_.exp2()),
						if_.loc()),
				let     -> {
					Id id = let.decl().id();
					List<IcPattern> args = let.decl().args();
					IcExp bounded = let.decl().body();
					CcExp letBody = compile(let.body());
					Location loc = let.loc();

					if(!args.isEmpty()) {
						return compileFunc(id, let.decl().args(), bounded)
								.map(mkCls -> (CcExp)new CcLet(id, mkCls, letBody, loc))
								.orElse(letBody); // トップレベルで定義されているのでletは要らない
					} else {
						return new CcLet(id, compile(bounded), letBody, loc);
					}
				},
				letrec -> {
					if(letrec.decls().size() != 1) {
						todo();
					}
					IcDecl decl = letrec.decls().get(0);
					Id id = decl.id();
					List<IcPattern> args = decl.args();
					IcExp bounded = decl.body();
					CcExp letBody = compile(letrec.body());
					Location loc = letrec.loc();
					if(!args.isEmpty()) {
						return compileFunc(id, decl.args(), bounded)
								.map(mkCls -> (CcExp)new CcLet(id, mkCls, letBody, loc))
								.orElse(letBody); // トップレベルで定義されているのでletは要らない
					} else {
						return new CcLet(id, compile(bounded), letBody, loc);
					}
				},
				case_ -> {
					CcExp target = compile(case_.target());
					List<CcCaseBranch> branches =
							case_.branches().stream()
									.map(branch -> new CcCaseBranch(
											branch.pattern(),
											compile(branch.body()),
											branch.loc()))
									.toList();
					return new CcCase(target, branches, case_.loc());
				});
	}

	private IdList fvFunc(CcExp body, List<IcPattern> args) {
		IdList free = new IdList();
		Set<Id> bounded = new HashSet<>(knowns);
		args.forEach(arg -> arg.addVars(bounded));
		fv(body, bounded, free);
		return free;
	}

	private void fv(CcExp exp, Set<Id> bounded, IdList free) {
		exp.match(
				cnst -> {},
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
					bounded.add(let.boundVar());
					fv(let.boundExp(), bounded, free);
					fv(let.mainExp(), bounded, free);
				},
				case_ -> {
					fv(case_.target(), bounded, free);
					for(CcCaseBranch branch: case_.branches()) {
						branch.pattern().addVars(bounded);
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

	private CcType convert(IcType icType) {
		return new CcType(icType.id(), icType.ctors().stream().map(ctor -> convert(ctor)).toList(), icType.loc());
	}

	private CcCtor convert(IcCtor icCtor) {
		return new CcCtor(icCtor.id(), icCtor.args(), icCtor.loc());
	}
}
