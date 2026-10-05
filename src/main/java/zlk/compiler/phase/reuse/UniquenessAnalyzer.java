package zlk.compiler.phase.reuse;

import zlk.compiler.id.IdMap;
import zlk.compiler.ir.reuse.LocalMap;
import zlk.compiler.ir.reuse.LocalSet;
import zlk.compiler.ir.reuse.LocalVar;
import zlk.compiler.ir.reuse.own.OwnBlock;
import zlk.compiler.ir.reuse.own.OwnBranch;
import zlk.compiler.ir.reuse.own.OwnFunDecl;
import zlk.compiler.ir.reuse.own.OwnModule;
import zlk.compiler.ir.reuse.own.OwnRhs;
import zlk.compiler.ir.reuse.own.OwnStmt;
import zlk.compiler.ir.reuse.own.OwnUse;
import zlk.compiler.ir.reuse.own.Ownership;
import zlk.compiler.ir.reuse.plan.ModuleUniquenessFacts;
import zlk.compiler.ir.reuse.plan.UniquenessFacts;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

public final class UniquenessAnalyzer {
	// 各siteに関連するuniqueな変数（site自身を除く）
	private LocalMap<Seq<LocalVar>> uniquenessInfo;
	// 解析中のuniqueな変数
	private LocalSet uniques;

	private UniquenessAnalyzer(int idSize) {
		this.uniquenessInfo = new LocalMap<>(idSize);
		this.uniques = new LocalSet(idSize);
	}

	public static ModuleUniquenessFacts analyze(OwnModule module) {
		IdMap<UniquenessFacts> result = new IdMap<>();
		module.funcs().forEach(fun -> result.put(fun.id(), analyze(fun)));
		return new ModuleUniquenessFacts(result);
	}

	public static UniquenessFacts analyze(OwnFunDecl decl) {
		UniquenessAnalyzer yzer = new UniquenessAnalyzer(decl.localIdSize());
		decl.body().stmts().forEach(stmt -> yzer.analyze(stmt));
		return new UniquenessFacts(yzer.uniquenessInfo);
	}

	public LocalSet analyze(OwnBlock block) {
		block.stmts().forEach(stmt -> analyze(stmt));
		return uniques;
	}

	public void analyze(OwnStmt stmt) {
		switch(stmt) {
		case OwnStmt.Bind(LocalVar lhs, Ownership _, OwnRhs rhs, _) -> {
			// RHS評価直前の状態を記録
			SeqBuffer<LocalVar> uniquesAtSite = new SeqBuffer<>();
			rhs.walkDirectUses(use -> {
				LocalVar var = use.var();
				if(uniques.contains(var)) {
					uniquesAtSite.addIfNotContains(var);
				}
			});
			uniquenessInfo.put(lhs, uniquesAtSite.toSeq());

			switch(rhs) {
			case OwnRhs.MakeRecord _ -> {
				uniques.add(lhs);
			}
			case OwnRhs.Cnst _,  // 同じ実体の可能性
				OwnRhs.DirectApp _,
				OwnRhs.ClosureApp _,
				OwnRhs.MakeClosure _,  // JVMが使いまわすかも
				OwnRhs.RecordGet _ -> {
				// これらはUNIQUEではない
			}
			case OwnRhs.If(OwnUse _, OwnBlock thenBlock, OwnBlock elseBlock) -> {
				LocalSet beforeBranchUniques = new LocalSet(uniques);
				LocalSet thenUniques = analyze(thenBlock);
				uniques = new LocalSet(beforeBranchUniques);
				LocalSet elseUniques = analyze(elseBlock);

				// 合流後のuniqueness
				uniques = LocalSet.intersect(thenUniques, elseUniques);

				if(thenUniques.contains(thenBlock.result().var())
						&& elseUniques.contains(elseBlock.result().var())) {
					uniques.add(lhs);
				}
			}
			case OwnRhs.Case(OwnUse _, Seq<OwnBranch> branches) -> {
				LocalSet beforeBranchUniques = new LocalSet(uniques);
				LocalSet resultUniques = new LocalSet(uniques);
				boolean isLhsUnique = true;

				boolean isFirstBranch = true;
				for(OwnBranch branch : branches) {
					if(isFirstBranch) {
						isFirstBranch = false;
					} else {
						uniques = new LocalSet(beforeBranchUniques);
					}

					analyze(branch.body());
					resultUniques.retainAll(uniques);
					isLhsUnique &= uniques.contains(branch.body().result().var());
				}

				uniques = resultUniques;
				if(isLhsUnique) {
					uniques.add(lhs);
				}
			}
			case OwnRhs.RecordUpdate _ -> {
				uniques.add(lhs);
			}
			}
		}

		case OwnStmt.Dup(LocalVar var, _) -> {
			// 一度でもDupした変数はUNKNOWN
			uniques.removeIfContains(var);
		}
		case OwnStmt.Drop _ -> {
			// TODO: 参照のカウント
		}
		}
	}
}
