package zlk.phase.reuse;

import zlk.common.ConstValue;
import zlk.common.Location;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.ir.reuse.LocalMap;
import zlk.ir.reuse.LocalSet;
import zlk.ir.reuse.LocalVar;
import zlk.ir.reuse.anf.AnfBind;
import zlk.ir.reuse.anf.AnfBlock;
import zlk.ir.reuse.anf.AnfBranch;
import zlk.ir.reuse.anf.AnfFunDecl;
import zlk.ir.reuse.anf.AnfModule;
import zlk.ir.reuse.anf.AnfPattern;
import zlk.ir.reuse.anf.AnfPattern.Var;
import zlk.ir.reuse.anf.AnfRhs;
import zlk.ir.reuse.anf.AnfRhs.AnfRecordField;
import zlk.ir.reuse.own.OwnBlock;
import zlk.ir.reuse.own.OwnBranch;
import zlk.ir.reuse.own.OwnFunDecl;
import zlk.ir.reuse.own.OwnModule;
import zlk.ir.reuse.own.OwnPattern;
import zlk.ir.reuse.own.OwnRhs;
import zlk.ir.reuse.own.OwnRhs.OwnRecordField;
import zlk.ir.reuse.own.OwnStmt;
import zlk.ir.reuse.own.OwnStmt.Drop;
import zlk.ir.reuse.own.OwnUse;
import zlk.ir.reuse.own.Ownership;
import zlk.ir.reuse.own.UseMode;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

/**
 * AnfIRへ所有権情報を付与し，Dup/Dropを明示したOwnIRへ変換する
 */
public final class OwnershipElaborator {

	private OwnershipElaborator() {}

	public static OwnModule convert(AnfModule src) {
		return new OwnModule(
				src.name(),
				src.types(),
				src.funcs().map(FunctionElaborator::convert));
	}
}

final class FunctionElaborator {
	static OwnFunDecl convert(AnfFunDecl decl) {
		FunctionElaborator self = new FunctionElaborator(decl);

		// binderのownershipを解析
		decl.args().forEach(arg ->
			arg.walkVars(var ->
				self.ownerships.put(var, Ownership.OWNED)));  // とりあえず全部OWNEDする
		self.extractOwnership(decl.body());

		// 生存解析してDup/Dropを設定
		Seq<OwnPattern> args = decl.args().map(self::convert);
		OwnBlock body = self.new BlockElaborator().convert(decl.body()).ownBlock;

		return new OwnFunDecl(decl.id(), args, body, self.localIdSize, decl.loc());
	}

	private final int localIdSize;
	private final LocalMap<Ownership> ownerships;
	private FunctionElaborator(AnfFunDecl decl) {
		this.localIdSize = decl.localIdSize();
		this.ownerships = new LocalMap<Ownership>(localIdSize);
	}

	/**
	 * その値の所有権を抽出する
	 *
	 * BORROWEDは読取り専用，OWNEDは読み書き可能．
	 * @param body
	 */
	private void extractOwnership(AnfBlock body) {
		body.binds().forEach(bind -> {
			LocalVar lhs = bind.dst();
			switch(bind.rhs()) {
			case AnfRhs.Cnst _ -> {
				ownerships.put(lhs, Ownership.OWNED);
			}
			case AnfRhs.DirectApp _ -> {
				ownerships.put(lhs, Ownership.OWNED);
			}
			case AnfRhs.ClosureApp _ -> {
				ownerships.put(lhs, Ownership.OWNED);
			}
			case AnfRhs.MakeClosure _ -> {
				ownerships.put(lhs, Ownership.OWNED);
			}
			case AnfRhs.If(_, AnfBlock thenBlock, AnfBlock elseBlock) -> {
				extractOwnership(thenBlock);
				extractOwnership(elseBlock);
				// 結果の値はとりあえずOWNEDであわせることにする
				ownerships.put(lhs, Ownership.OWNED);
			}
			case AnfRhs.Case(LocalVar target, Seq<AnfBranch> branches) -> {
				// targetはBORROWで問題ないかも
				Ownership ownership_ = ownerships.get(target);
				branches.forEach(branch -> {
					branch.pattern().walkVars(var ->
						ownerships.put(var, ownership_));
					extractOwnership(branch.body());
				});
				// 最後はOWNEDの方が楽そう
				ownerships.put(lhs, Ownership.OWNED);
			}
			case AnfRhs.MakeRecord _ -> {
				ownerships.put(lhs, Ownership.OWNED);
			}
			case AnfRhs.RecordGet _ -> {  // 取り出した値は常にOWNED
				ownerships.put(lhs, Ownership.OWNED);
			}
			case AnfRhs.RecordUpdate _ -> {
				ownerships.put(lhs, Ownership.OWNED);
			}
			}
		});
	}

	private OwnPattern convert(AnfPattern pat) {
		return switch(pat) {
		case AnfPattern.Wildcard(Type type, Location loc) ->
				new OwnPattern.Wildcard(type, loc);
		case AnfPattern.Var var ->
				convert(var);
		case AnfPattern.Ctor(Id ctor, Seq<AnfPattern> args, Type type, Location loc) ->
				new OwnPattern.Ctor(ctor, args.map(this::convert), type, loc);
		case AnfPattern.Record(Seq<Var> fields, Type type, Location loc) ->
				new OwnPattern.Record(fields.map(this::convert), type, loc);
		};
	}
	private OwnPattern.Var convert(AnfPattern.Var var) {
		return new OwnPattern.Var(var.var(), ownerships.get(var.var()), var.loc());
	}

	record BlockAndAlives(OwnBlock ownBlock, LocalSet alives) {}

	final class BlockElaborator {
		private final SeqBuffer<OwnStmt> revStmts;
		private LocalSet alives;

		BlockElaborator() {
			revStmts = new SeqBuffer<>();
			alives = new LocalSet(localIdSize);
		}
		BlockElaborator(LocalSet future) {
			revStmts = new SeqBuffer<>();
			alives = new LocalSet(future);
		}

		private BlockAndAlives convert(AnfBlock body) {
			// 戻り値となるオブジェクトはブロック最後まで生存する
			OwnUse result = new OwnUse(body.result(), UseMode.TAKE);
			dupIfTakenAndCannotMove(result, body.loc());

			// 後ろ向きに走査しながら必要なだけ参照を増減させる
			body.binds().reversed().forEach(this::convert);

			return new BlockAndAlives(
					new OwnBlock(
							revStmts.toSeq().reversed(),
							new OwnUse(body.result(), UseMode.TAKE),  // TODO 戻り値にTAKE/BORROWあるの？
							body.loc()
					),
					alives
			);
		}

		/**
		 * {@link AnfBind}を変換する．すなわちrevStmtsとalivesに対応する要素を追加する．
		 *
		 * @param src
		 */
		private void convert(AnfBind src) {
			LocalVar lhs = src.dst();
			Location loc = src.loc();

			if (!alives.contains(lhs)) {
				// 左辺が利用されない
				// 本来停止しないところが停止する可能性がでるが許容
				// TODO: 未使用変数をlogに出力 コンパイルオプションで切替
				return;
			}

			// 各命令の変換は
			// 1. 各call siteのUseModeを決定（暫定）
			// 2. 以下を逆順にrevStmtsに追加
			//   2-1. 必要な値の所有権の確保(Dup)
			//   2-2. 命令本体の処理
			//   2-3. 不用な所有権の破棄(Drop)
			// 3. 生存中の変数の追加
			switch(src.rhs()) {
			case AnfRhs.Cnst(ConstValue value) -> {
				bind(lhs, new OwnRhs.Cnst(value), loc);
			}
			case AnfRhs.DirectApp(Id fun, Seq<LocalVar> args) -> {
				// 引数はとりあえず全部TAKEとする
				Seq<OwnUse> args_ = args.map(arg -> new OwnUse(arg, UseMode.TAKE));

				// TODO 未使用pattern binderの削除
				// branchとまとめるとよさそう elaboratePatternBoundary(pattern, bodyResult) みたいな
				// コンパイルオプションで切り替えたい
				dropOwnedAfterLastUse(args_, src.loc());

				bind(lhs, new OwnRhs.DirectApp(fun, args_), loc);

				dupIfTakenAndCannotMove(args_, loc);
			}
			case AnfRhs.ClosureApp(LocalVar cls, Seq<LocalVar> args) -> {
				// クロージャも引数もとりあえず全部TAKEとする
				Seq<LocalVar> vars = Seq.concat(Seq.of(cls), args);
				Seq<OwnUse> vars_ = vars.map(var -> new OwnUse(var, UseMode.TAKE));

				dropOwnedAfterLastUse(vars_, src.loc());

				bind(lhs, new OwnRhs.ClosureApp(vars_.head(), vars_.tail()), loc);

				dupIfTakenAndCannotMove(vars_, loc);
			}
			case AnfRhs.MakeClosure(Id impl, Seq<LocalVar> caps) -> {
				// キャプチャはTAKEで確定か
				Seq<OwnUse> caps_ = caps.map(cap -> new OwnUse(cap, UseMode.TAKE));

				dropOwnedAfterLastUse(caps_, src.loc());

				bind(lhs, new OwnRhs.MakeClosure(impl, caps_), loc);

				dupIfTakenAndCannotMove(caps_, loc);
			}
			case AnfRhs.If(LocalVar condition, AnfBlock thenBlock, AnfBlock elseBlock) -> {
				LocalSet future = new LocalSet(alives);
				future.removeIfContains(lhs);

				// 分岐毎に変換して合流の辻褄を合せる
				BlockAndAlives thenResult = new BlockElaborator(future).convert(thenBlock);
				BlockAndAlives elseResult = new BlockElaborator(future).convert(elseBlock);

				// 分岐前に生存している変数
				LocalSet unionAlives = new LocalSet(localIdSize);
				thenResult.alives.forEach(unionAlives::addIfNotContains);
				elseResult.alives.forEach(unionAlives::addIfNotContains);

				// 自分のブランチに関係なくて所有権のある値をDrop
				Seq<Drop> dropsBeforeThen = unionAlives.toSeq()
						.filter(var -> !thenResult.alives.contains(var)
								&& ownerships.get(var) == Ownership.OWNED)
						.map(var -> new OwnStmt.Drop(var, thenBlock.loc()));

				Seq<Drop> dropsBeforeElse = unionAlives.toSeq()
						.filter(var -> !elseResult.alives.contains(var)
								&& ownerships.get(var) == Ownership.OWNED)
						.map(var -> new OwnStmt.Drop(var, elseBlock.loc()));

				OwnUse condition_ = new OwnUse(condition, UseMode.BORROW);
				dropOwnedAfterLastUse(condition_, loc);
				bind(lhs, new OwnRhs.If(
						condition_,
						thenResult.ownBlock.insertStmts(dropsBeforeThen),
						elseResult.ownBlock.insertStmts(dropsBeforeElse)
				),loc);

				// conditionはBORROWEDなのでdupは不用

				alives = unionAlives;
				alives.addIfNotContains(condition);
			}
			case AnfRhs.Case(LocalVar target, Seq<AnfBranch> branches) -> {
				LocalSet future = new LocalSet(alives);
				future.removeIfContains(lhs);

				// 分岐ごとの処理
				Seq<BranchAndAlives> converteds =
						branches.map(branch -> FunctionElaborator.this.convertBranch(
								branch,
								future
						));

				// 分岐前の生存変数
				LocalSet unionAlives = new LocalSet(localIdSize);
				converteds.map(BranchAndAlives::beforePatternAlives)
						.forEach(alives -> alives.forEach(unionAlives::addIfNotContains));

				// 各branchでは不要だが分岐までは保持する必要がある外側の変数をDrop
				Seq<OwnBranch> branches_ = converteds.map(converted -> {
					Seq<Drop> dropsBeforeBranch = unionAlives.toSeq()
							.filter(var -> !converted.beforePatternAlives().contains(var)
									&& ownerships.get(var) == Ownership.OWNED)
							.map(var -> new OwnStmt.Drop(var, converted.ownBranch().loc()));

					OwnBranch branch = converted.ownBranch();

					return new OwnBranch(
							branch.pattern(),
							branch.body().insertStmts(dropsBeforeBranch),
							branch.loc());
				});

				// targetは分解するのでTAKE？中身を書き換えないなら要らない？
				OwnUse target_ = new OwnUse(target, UseMode.TAKE);

				bind(lhs, new OwnRhs.Case(target_, branches_), loc);  // TODO: たぶんこのあたりのlocが正しくない

				alives = unionAlives;
				dupIfTakenAndCannotMove(target_, loc);
			}
			case AnfRhs.MakeRecord(Seq<AnfRecordField> fields) -> {
				// レコードに入れるものはさすがにTAKEでよさそう
				Seq<OwnUse> vars = fields.map(field -> new OwnUse(field.value(), UseMode.TAKE));

				dropOwnedAfterLastUse(vars, loc);

				Seq<OwnRecordField> fields_ = Seq.zip(fields, vars)
						.map((anf, var) -> new OwnRhs.OwnRecordField(anf.name(), var));
				bind(lhs, new OwnRhs.MakeRecord(fields_), loc);

				dupIfTakenAndCannotMove(vars, loc);
			}
			case AnfRhs.RecordGet(LocalVar target, String field) -> {
				 OwnUse target_ = new OwnUse(target, UseMode.BORROW);

				dropOwnedAfterLastUse(target_, loc);

				// RecordGet自身がfieldをretainしOWNEDな結果を返す
				bind(lhs, new OwnRhs.RecordGet(target_, field), loc);

				alives.addIfNotContains(target);
			}
			case AnfRhs.RecordUpdate(LocalVar target, Seq<AnfRecordField> fields) -> {

				// targetのownershipを更新処理へ渡す
				Seq<OwnUse> vars = Seq.concat(Seq.of(target), fields.map(AnfRecordField::value))
						.map(var -> new OwnUse(var, UseMode.TAKE));

				dropOwnedAfterLastUse(vars, loc);

				bind(lhs, new OwnRhs.RecordUpdate(
						vars.head(),
						Seq.zip(fields, vars.tail()).map((field, var) -> new OwnRecordField(field.name(), var))),
						loc);

				dupIfTakenAndCannotMove(vars, loc);
			}
			}
		}

		/**
		 * OWNEDな変数の最後の出現がBORROWならDropを追加する．
		 * @param usage 変数
		 * @Location loc Dropの挿入位置
		 */
		private void dropOwnedAfterLastUse(OwnUse usage, Location loc) {
			LocalVar var = usage.var();
			if(ownerships.get(var) == Ownership.OWNED  // 所有権を持つもの
					&& !alives.contains(var)  // この先現れない
					&& usage.mode() == UseMode.BORROW  // TAKEなら所有権は消費先に移る
			) {
				revStmts.add(new OwnStmt.Drop(var, loc));
			}
		}
		/**
		 * OWNEDな変数の最後の出現がBORROWならDropを追加する．
		 * @param usage 変数
		 * @Location loc Dropの挿入位置
		 */
		private void dropOwnedAfterLastUse(Seq<OwnUse> usages, Location loc) {
			usages.reversed()  // revStmtsへの追加なので順番は反転
					.forEach(usage -> dropOwnedAfterLastUse(usage, loc));
		}

		/**
		 * Bindを追加して生存期間を閉じる．
		 * @param lhs
		 * @param rhs
		 * @param loc
		 * @return
		 */
		private void bind(LocalVar lhs, OwnRhs rhs, Location loc) {
			revStmts.add(new OwnStmt.Bind(lhs, ownerships.get(lhs), rhs, loc));
			alives.remove(lhs);
		}

		/**
		 * 利用箇所に必要なDupを追加する．
		 * @param vars
		 * @param loc
		 */
		private void dupIfTakenAndCannotMove(OwnUse usage, Location loc) {
			LocalVar var = usage.var();
			if(usage.mode() == UseMode.TAKE  // call-siteで所有権が必要
					&& !(ownerships.get(var) == Ownership.OWNED && !alives.contains(var))  // 単純にmoveできない
			) {
				revStmts.add(new OwnStmt.Dup(var, loc));
			}

			// 利用箇所なので出現していなければしたことに
			// これを怠るとf x xのようなパターンで不正
			alives.addIfNotContains(var);
		}
		private void dupIfTakenAndCannotMove(Seq<OwnUse> usages, Location loc) {
			usages.reversed()  // revStmtsへの追加なので順番は反転
					.forEach(usage -> dupIfTakenAndCannotMove(usage, loc));
		}
	}

	private record BranchAndAlives(
			OwnBranch ownBranch,
			LocalSet beforePatternAlives
	) {}

	private BranchAndAlives convertBranch(AnfBranch branch, LocalSet future) {
		// branchのpattern binderのdropがBlockElaboratorだと難しかったのでここに分離

		BlockAndAlives bodyResult = new BlockElaborator(future).convert(branch.body());
		LocalSet beforePatternAlives = new LocalSet(bodyResult.alives());

		SeqBuffer<Drop> unusedPatternDrops = new SeqBuffer<>();
		branch.pattern().walkVars(var -> {
			if (beforePatternAlives.contains(var)) {
				// body で使用される pattern binder
				// binder は pattern match によってここで生成されるので
				// case より前の liveness には伝播させない
				beforePatternAlives.remove(var);
			} else if (ownerships.get(var) == Ownership.OWNED) {
				// pattern match で所有権を得たが
				// body では一度も使用されない binder
				unusedPatternDrops.add(new OwnStmt.Drop(var, branch.loc()));
			}
		});

		OwnBlock body = bodyResult.ownBlock().insertStmts(unusedPatternDrops.toSeq());

		return new BranchAndAlives(
				new OwnBranch(convert(branch.pattern()), body, branch.loc()),
				beforePatternAlives);
	}
}