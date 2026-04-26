package zlk.recon;

import java.util.function.Consumer;

import zlk.common.ConstValue;
import zlk.common.Location;
import zlk.common.Type;
import zlk.common.id.Id;
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
import zlk.util.collection.SeqBuffer;

public class LetDependencyExtractor {
	private LetDependencyExtractor() {}

	/**
	 * 依存されている先を抽出する
	 * @param module
	 * @return
	 */
	public static IdMap<Seq<Id>> extract(IcModule module) {
		IdMap<Seq<Id>> includes = new IdMap<>();
		accIncluded(module.decls(), includes, null);
		// 依存されている側から引く（letで定義されるものだけでよい）
		IdMap<SeqBuffer<Id>> dependency = new IdMap<>();
		includes.keys().forEach(k -> dependency.put(k, new SeqBuffer<>()));
		includes.forEach(
				(d, es) -> es
						.filter(dependency::containsKey)
						.forEach(e -> dependency.get(e).addIfNotContains(d))
		);
		return dependency.traverse(SeqBuffer::toSeq);
	}

	private static void accIncluded(Seq<IcValDecl> decls, IdMap<Seq<Id>> revRel, SeqBuffer<Id> acc) {
		for (IcValDecl decl : decls) {
			SeqBuffer<Id> partial = new SeqBuffer<>();
			accIncluded(decl.body(), revRel, partial);
			revRel.put(decl.id(), partial.toSeq());
			if(acc != null) {
				acc.addAll(partial.filter(id -> !acc.contains(id)));
			}
		}
	}

	private static void accIncluded(IcExp exp, IdMap<Seq<Id>> includes, SeqBuffer<Id> acc) {
		Consumer<IcExp> go = exp_ -> accIncluded(exp_, includes, acc);

		switch (exp) {
		case IcCnst(ConstValue _, Location _) -> {}
		case IcVarLocal(Id id, Location _) -> {
			acc.addIfNotContains(id);
		}
		case IcVarForeign(Id _, Type _, Location _) -> {}
		case IcVarCtor(Id _, Type _, Location _) -> {}
		case IcLamb(Seq<IcPattern> _, IcExp body, Location _) -> {
			accIncluded(body, includes, acc);
		}
		case IcApp(IcExp fun, Seq<IcExp> args, Location _) -> {
			accIncluded(fun, includes, acc);
			args.forEach(go);
		}
		case IcIf(IcExp cond, IcExp thenExp, IcExp elseExp, Location _) -> {
			Seq.of(cond, thenExp, elseExp).forEach(go);
		}
		case IcLet(Seq<IcValDecl> decls, IcExp body, Location _) -> {
			accIncluded(decls, includes, acc);
			accIncluded(body, includes, acc);
		}
		case IcCase(IcExp target, Seq<IcCaseBranch> branches, Location _) -> {
			accIncluded(target, includes, acc);
			branches.map(IcCaseBranch::body).forEach(go);
		}
		};
	}
}
