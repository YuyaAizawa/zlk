package zlk.clconv;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import zlk.clcalc.CcCall;
import zlk.clcalc.CcConst;
import zlk.clcalc.CcDecl;
import zlk.clcalc.CcExp;
import zlk.clcalc.CcIf;
import zlk.clcalc.CcLet;
import zlk.clcalc.CcMkCls;
import zlk.clcalc.CcModule;
import zlk.clcalc.CcVar;
import zlk.common.Id;
import zlk.common.IdGenerator;
import zlk.common.IdList;
import zlk.common.Type;
import zlk.core.Builtin;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcModule;
import zlk.typecheck.TypeChecker;

public final class ClosureConveter {

	private final IcModule src;
	private final Map<Id, Builtin> builtins;
	private final IdGenerator idGenerator;
	private final IdList toplevelIds;

	private final List<CcDecl> toplevels;

	public ClosureConveter(IcModule src, Map<Id, Builtin> builtins, IdGenerator idGenerator) {
		this.src = src;
		this.builtins = builtins;
		this.idGenerator = idGenerator;
		this.toplevelIds = new IdList(src.decls().stream().map(IcDecl::id).toList());
		this.toplevels = new ArrayList<>();
	}

	public CcModule convert() {
		src.decls().forEach(this::compileFunc);

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
			toplevels.add(new CcDecl(decl.id(), decl.args(), decl.type(), body));
			toplevelIds.add(decl.id());
			return Optional.empty();

		} else if(frees.size() == 1 && frees.get(0).equals(decl.id())) {
			System.out.println(decl.name() + " is self-recursive and not closure.");
			toplevels.add(new CcDecl(decl.id(), decl.args(), decl.type(), body));
			toplevelIds.add(decl.id());
			return Optional.empty();

		} else {
			System.out.println(decl.name() + " is cloeure. frees: "+frees);
			String clsName = decl.name(); // TODO ユニークな名前
			CcDecl closureFunc = makeClosure(clsName, frees, decl.args(), body, decl.returnTy());
			toplevels.add(closureFunc);
			toplevelIds.add(closureFunc.id());
			return Optional.of(new CcMkCls(closureFunc.id(), frees));
		}
	}

	private CcDecl makeClosure(String clsName, IdList frees, IdList originalArgs, CcExp body, Type retTy) {
		Type[] types =
				Stream.concat(Stream.concat(
						frees.stream().map(Id::type),
						originalArgs.stream().map(Id::type)),
						Stream.of(retTy))
						.toArray(Type[]::new);
		Type clsTy = Type.arrow(types);
		Id clsId = idGenerator.generate(clsName, clsTy);


		Map<Id, Id> idMap =
				frees.stream().collect(Collectors.toMap(
						id -> id,
						id -> idGenerator.generate(id.name(), id.type())));
		IdList clsArgs = new IdList();
		clsArgs.addAll(originalArgs);
		frees.forEach(id -> clsArgs.add(idMap.get(id)));

		CcExp clsBody = body.substId(idMap);

		return new CcDecl(clsId, clsArgs, clsTy, clsBody);
	}

	private CcExp compile(IcExp body) {
		return body.fold(
				cnst -> new CcConst(cnst.cnst()),
				var  -> new CcVar(var.idInfo()),
				app  -> {
					CcExp funExp = compile(app.fun());
					List<CcExp> argExps =
							app.args().stream()
							.map(arg -> compile(arg)).toList();
					Type type = TypeChecker.check(app);
					return new CcCall(funExp, argExps, type);
				},
				if_  -> new CcIf(
						compile(if_.cond()),
						compile(if_.exp1()),
						compile(if_.exp2())),
				let  -> {
					if(let.decl().args().size() == 0) {
						return new CcLet(
								let.decl().id(),
								compile(let.decl().body()),
								compile(let.body()),
								let.decl().type());
					}

					CcExp bodyExp = compile(let.body());
					return compileFunc(let.decl()).map(
							mkCls -> (CcExp)new CcLet(let.decl().id(), mkCls, bodyExp, let.decl().type())
					).orElse(bodyExp);
				});
	}

	private IdList fvFunc(CcExp body, IdList args) {
		IdList free = new IdList();
		Set<Id> bounded = new HashSet<>((builtins.size() + toplevelIds.size() + args.size()) * 2);
		bounded.addAll(builtins.keySet());
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
