package zlk.recon;

import java.util.List;

import zlk.common.ConstValue;
import zlk.common.Location;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.idcalc.IcCaseBranch;
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
import zlk.idcalc.IcValDecl;
import zlk.util.collection.Seq;

public class LetDependencyExtractor {
	private LetDependencyExtractor() {}

	/**
	 * 依存されている先を抽出する
	 * @param module
	 * @return
	 */
	public static IdMap<IdList> extract(IcModule module) {
		IdMap<IdList> includes = new IdMap<>();
		accIncluded(module.decls().toList(), includes);
		// 依存されている側から引く（letで定義されるものだけでよい）
		IdMap<IdList> dependency = new IdMap<>();
		includes.keys().forEach(k -> dependency.put(k, new IdList()));
		includes.forEach((d, es) -> es.stream()
				.filter(dependency::containsKey)
				.forEach(e -> dependency.get(e).addIfNotContains(d)));
		return dependency;
	}

	private static IdList accIncluded(List<IcValDecl> decls, IdMap<IdList> revRel) {
		IdList all = new IdList();
		for (IcValDecl decl : decls) {
			IdList partial = accIncluded(decl.body(), revRel);
			revRel.put(decl.id(), partial);
			partial.forEach(all::addIfNotContains);
		}
		return all;
	}

	private static IdList accIncluded(IcExp exp, IdMap<IdList> includes) {
		return switch (exp) {
		case IcCnst(ConstValue _, Location _) -> {
			yield new IdList();
		}
		case IcVarLocal(Id id, Location _) -> {
			IdList result = new IdList();
			result.add(id);
			yield result;
		}
		case IcVarForeign(Id _, Type _, Location _) -> {
			yield new IdList();
		}
		case IcVarCtor(Id _, Type _, Location _) -> {
			yield new IdList();
		}
		case IcLamb(Seq<IcPattern> _, IcExp body, Location _) -> {
			yield accIncluded(body, includes);
		}
		case IcApp(IcExp fun, Seq<IcExp> args, Location _) -> {
			IdList result = accIncluded(fun, includes);
			args.forEach(arg -> accIncluded(arg, includes).forEach(result::addIfNotContains));
			yield result;
		}
		case IcIf(IcExp cond, IcExp thenExp, IcExp elseExp, Location _) -> {
			IdList result = accIncluded(cond, includes);
			accIncluded(thenExp, includes).forEach(result::addIfNotContains);
			accIncluded(elseExp, includes).forEach(result::addIfNotContains);
			yield result;
		}
		case IcLet(Seq<IcValDecl> decls, IcExp body, Location _) -> {
			IdList result = accIncluded(decls.toList(), includes);
			accIncluded(body, includes).forEach(result::addIfNotContains);
			yield result;
		}
		case IcCase(IcExp target, Seq<IcCaseBranch> branches, Location _) -> {
			IdList result = accIncluded(target, includes);
			branches.forEach(branch -> accIncluded(branch.body(), includes).forEach(result::addIfNotContains));
			yield result;
		}
		};
	}
}
