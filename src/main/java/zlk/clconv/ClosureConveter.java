package zlk.clconv;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import zlk.clcalc.CcCall;
import zlk.clcalc.CcCnst;
import zlk.clcalc.CcDecl;
import zlk.clcalc.CcExp;
import zlk.clcalc.CcIf;
import zlk.clcalc.CcLet;
import zlk.clcalc.CcMkCls;
import zlk.clcalc.CcModule;
import zlk.clcalc.CcVar;
import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.common.type.Type;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcModule;
import zlk.util.Location;

public final class ClosureConveter {

	private final IcModule src;
	private final IdList builtins;
	private final IdMap<Type> type; // 変換後のIdの型を登録
	private final IdList toplevelIds;

	private final List<CcDecl> toplevels;

	public ClosureConveter(IcModule src, IdMap<Type> type, IdList builtins) {
		this.src = src;
		this.type = type;
		this.builtins = builtins;
		this.toplevelIds = new IdList(src.decls().stream().map(IcDecl::id).toList());
		this.toplevels = new ArrayList<>();
	}

	public CcModule convert() {
		src.decls().forEach(decl -> compileFunc(decl));

		return new CcModule(src.name(), toplevels, src.origin());
	}

	/**
	 * 関数をトップレベルに変換する．クロージャに変換された場合，その作成を返す．
	 * @return クロージャの生成（クロージャが必要だったとき）
	 */
	private Optional<CcMkCls> compileFunc(IcDecl decl) {

		CcExp body = compile(decl.body());
		IdList frees = fvFunc(body, decl.args());

		if(frees.isEmpty()) {
			System.out.println(decl.name() + " is not cloeure.");
			toplevels.add(new CcDecl(decl.id(), decl.args(), body, decl.loc()));
			toplevelIds.add(decl.id());
			return Optional.empty();

		} else if(frees.size() == 1 && frees.get(0).equals(decl.id())) {
			System.out.println(decl.name() + " is self-recursive and not closure.");
			toplevels.add(new CcDecl(decl.id(), decl.args(), body, decl.loc()));
			toplevelIds.add(decl.id());
			return Optional.empty();

		} else {
			System.out.println(decl.name() + " is cloeure. frees: "+frees);
			CcDecl closureFunc = makeClosure(decl.name(), frees, decl.args(), body, decl.returnTy(), decl.loc());
			toplevels.add(closureFunc);
			toplevelIds.add(closureFunc.id());
			return Optional.of(new CcMkCls(closureFunc.id(), frees, closureFunc.loc()));
		}
	}

	private CcDecl makeClosure(String clsName, IdList frees, IdList originalArgs, CcExp body, Type retTy, Location loc) {
		Type[] types =
				Stream.concat(Stream.concat(
						frees.stream().map(type::get),
						originalArgs.stream().map(type::get)),
						Stream.of(retTy))
						.toArray(Type[]::new);
		Type clsTy = Type.arrow(types);
		Id clsId = Id.fromPathAndSimpleName(src.name(), clsName);
		type.put(clsId, clsTy);

		IdMap<Id> idMap =
				frees.stream().collect(IdMap.collector(
						Function.identity(),
						id -> Id.fromParentAndSimpleName(clsId, id.simpleName())));
		IdList clsArgs = new IdList();
		clsArgs.addAll(originalArgs);
		for(Id free : frees) {
			Id newId = idMap.get(free);
			clsArgs.add(newId);
			type.put(newId, type.get(free));
		}

		CcExp clsBody = body.substId(idMap);

		return new CcDecl(clsId, clsArgs, clsBody, loc);
	}

	private CcExp compile(IcExp body) {
		return body.fold(
				cnst -> new CcCnst(cnst.value(), cnst.loc()),
				var  -> new CcVar(var.id(), var.loc()),
				app  -> {
					CcExp funExp = compile(app.fun());
					List<CcExp> argExps =
							app.args().stream()
							.map(arg -> compile(arg)).toList();
					return new CcCall(funExp, argExps, app.loc());
				},
				if_  -> new CcIf(
						compile(if_.cond()),
						compile(if_.exp1()),
						compile(if_.exp2()),
						if_.loc()),
				let  -> {
					if(let.decl().args().size() == 0) {
						return new CcLet(
								let.decl().id(),
								compile(let.decl().body()),
								compile(let.body()),
								let.loc());
					}

					CcExp bodyExp = compile(let.body());
					return compileFunc(let.decl()).map(
							mkCls -> (CcExp)new CcLet(let.decl().id(), mkCls, bodyExp, let.loc())
					).orElse(bodyExp);
				});
	}

	private IdList fvFunc(CcExp body, IdList args) {
		IdList free = new IdList();
		Set<Id> bounded = new HashSet<>((builtins.size() + toplevelIds.size() + args.size()) * 2);
		bounded.addAll(builtins);
		bounded.addAll(toplevelIds);
		bounded.addAll(args);
		fv(body, bounded, free);
		return free;
	}

	private void fv(CcExp exp, Set<Id> bounded, IdList free) {
		exp.match(
				cnst -> {},
				var  -> {
					if(!bounded.contains(var.id())) {
						free.add(var.id());
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
							.filter(id -> !bounded.contains(id))
							.forEach(free::add);
				},
				if_  -> {
					fv(if_.cond(), bounded, free);
					fv(if_.thenExp(), bounded, free);
					fv(if_.elseExp(), bounded, free);
				},
				let  -> {
					fv(let.boundExp(), bounded, free);
					bounded.add(let.boundVar());
					fv(let.mainExp(), bounded, free);
				});
	}
}
