package zlk.recon;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import zlk.common.RecordField;
import zlk.recon.FlatType.CtorApp1;
import zlk.recon.FlatType.Fun1;
import zlk.recon.FlatType.Record1;
import zlk.recon.FlatType.Row1;
import zlk.recon.constraint.Content;
import zlk.recon.constraint.Content.FlexVar;
import zlk.recon.constraint.Content.RigidVar;
import zlk.recon.constraint.Content.Structure;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

public final class Unify {

	private static void merge(
			Variable var1, VariableState state1,
			Variable var2, VariableState state2,
			Content content
	) {
		// state1.kind と state2.kind は unify/rowUnify で一致を確認済み．
		// そのkindをexplicitに保存する．2引数VariableState constructorはTYPEへ落とすため使わない．
		VariableState state = new VariableState(content, Math.min(state1.rank, state2.rank), state1.kind);
		var1.unite(var2, state);
	}

	/** row merge用．kind/rankに加えてforbidden labelsを和集合する． */
	private static void mergeRow(
			Variable var1, VariableState state1,
			Variable var2, VariableState state2,
			Content content
	) {
		Set<String> union = new HashSet<>(state1.forbiddenLabels);
		union.addAll(state2.forbiddenLabels);
		// 採用contentがその制約を満たすことを検証
		if(content instanceof Structure s && s.flatType() instanceof Row1 row1) {
			for(RecordField<Variable> field : row1.fields()) {
				if(union.contains(field.name())) {
					throw new Missmatch(
							"row field '" + field.name() + "' violates lacks constraint on merged tail");
				}
			}
		}
		VariableState state = new VariableState(
				content, Math.min(state1.rank, state2.rank), state1.kind, union);
		var1.unite(var2, state);
	}

	// TODO 単一化中に生成した型変数(恐らくレコード多相などの推論で出てくる)を返すように
	public static void unify(Variable u, Variable v) {
		unify(u, v, null, 0);
	}

	/**
	 * freshFlexとletRankを明示的に渡すoverload．
	 * open row差分unificationではfresh shared tailを生成するためにfreshFlexと現在letRankが必要．
	 * 既存closed/simple callからは2引数unify経由で呼べる．両側residualの
	 * open row単一化にはfresh variableと現在rankが必須である．
	 */
	public static void unify(Variable u, Variable v, FreshFlex freshFlex, int letRank) {
		if(u.isSame(v)) {
			return;
		}

		// kind mismatchの検査
		if(u.kind() != v.kind()) {
			throw new Missmatch();
		}

		// ROW-kind同士はrowUnifyへ
		if(u.kind() == Variable.Kind.ROW) {
			rowUnify(u, v, freshFlex, letRank);
			return;
		}

		VariableState uState = u.get();
		VariableState vState = v.get();
		switch(uState.content) {
		case FlexVar _ -> {
			switch(vState.content) {
			case FlexVar v_ -> merge(u, uState, v, vState, v_.name().isEmpty() ? uState.content : vState.content);
			case RigidVar _ -> merge(u, uState, v, vState, vState.content);
			case Structure _ -> merge(u, uState, v, vState, vState.content);
			case Content.Error e -> merge(u, uState, v, vState, e);
			}
		}
		case RigidVar _ -> {
			switch(vState.content) {
			case FlexVar _ -> merge(u, uState, v, vState, uState.content);
			case RigidVar _ -> throw new Missmatch();
			default -> throw new Missmatch();
			}
		}
		case Structure u_ -> {
			switch(vState.content) {
			case FlexVar _ -> merge(u, uState, v, vState, uState.content);
			case RigidVar _ -> throw new Missmatch();
			case Structure v_ -> {
				if(u_.flatType() instanceof CtorApp1 u__ && v_.flatType() instanceof CtorApp1 v__) {
					if(u__.id().equals(v__.id())) {
						Seq<Variable> args = u__.args();
						Seq<Variable> otherArgs = v__.args();
						if(args.size() != otherArgs.size()) {
							throw new Missmatch();
						}
						for(int i = 0;i < args.size();i++) {
							unify(args.at(i), otherArgs.at(i), freshFlex, letRank);
						}
					} else {
						throw new Missmatch();
					}
				} else if(u_.flatType() instanceof Record1 u__ && v_.flatType() instanceof Record1 v__) {
					// Record1同士: 内包するrow変数同士をrowUnify
					// 成功したらmerged content（Structure）をmergeする
					Variable mergedRowContent = rowUnify(u__.row(), v__.row(), freshFlex, letRank);
					// rowUnifyがmerge済みのrootを返すので，Record1として両rootをmergeする
					merge(u, uState, v, vState, new Structure(new Record1(mergedRowContent)));
				} else if(u_.flatType() instanceof Fun1 u__ && v_.flatType() instanceof Fun1 v__) {
					unify(u__.arg(), v__.arg(), freshFlex, letRank);
					unify(u__.ret(), v__.ret(), freshFlex, letRank);
					merge(u, uState, v, vState, uState.content);
				} else {
					throw new Missmatch();
				}
			}
			case Content.Error e -> merge(u, uState, v, vState, e);
			}
		}
		case Content.Error e -> merge(u, uState, v, vState, e);
		}
	}

	/**
	 * 2引数互換のrowUnify．freshFlexなし，closed row専用．
	 */
	static void rowUnify(Variable u, Variable v) {
		rowUnify(u, v, null, 0);
	}

	/**
	 * Row1を保持するROW-kind変数同士の単一化．
	 * label差分unificationを行い，open tailを再帰的に処理する．
	 *
	 * @return merge後のrow root（uとvのroot）．
	 *     このrootのcontentはRow1 structureを持つ．
	 */
	static Variable rowUnify(Variable u, Variable v, FreshFlex freshFlex, int letRank) {
		if(u.isSame(v)) {
			return u;
		}

		// kind mismatchの検査（両者ともROW-kindでなければならない）
		if(u.kind() != Variable.Kind.ROW || v.kind() != Variable.Kind.ROW) {
			throw new Missmatch();
		}

		VariableState uState = u.get();
		VariableState vState = v.get();
		// flex/rigid/error の単純merge
		switch(uState.content) {
		case FlexVar _ -> {
			switch(vState.content) {
			case FlexVar v_ -> {
				// forbiddenを和集合してmerge
				Content mergedContent = v_.name().isEmpty() ? uState.content : vState.content;
				mergeRow(u, uState, v, vState, mergedContent);
				return u;
			}
			case RigidVar _ -> {
				// flex + rigid → rigid採用．flex側のforbiddenはrigid側へ伝播済みで
				// mergeRowで和集合する．ただしrigid側のcontentはstructureではないので
				// forbidden conflictは起きない．
				mergeRow(u, uState, v, vState, vState.content);
				return u;
			}
			case Structure _ -> {
				// flex + structure → structure採用．
				// structureのfieldsとflex側forbiddenの衝突をmergeRowで検査する．
				if(occursAnywhere(v, u)) {
					throw new Missmatch("recursive row during unification");
				}
				mergeRow(u, uState, v, vState, vState.content);
				return u;
			}
			case Content.Error e -> mergeRow(u, uState, v, vState, e);
			}
			return u;
		}
		case RigidVar _ -> {
			switch(vState.content) {
			case FlexVar _ -> {
				mergeRow(u, uState, v, vState, uState.content);
				return u;
			}
			case RigidVar _ -> throw new Missmatch();
			default -> throw new Missmatch();
			}
		}
		case Structure u_ -> {
			switch(vState.content) {
			case FlexVar _ -> {
				if(occursAnywhere(u, v)) {
					throw new Missmatch("recursive row during unification");
				}
				mergeRow(u, uState, v, vState, uState.content);
				return u;
			}
			case RigidVar _ -> throw new Missmatch();
			case Structure v_ -> {
				if(u_.flatType() instanceof Row1 uRow && v_.flatType() instanceof Row1 vRow) {
					return rowUnifyRow1(u, uState, uRow, v, vState, vRow, freshFlex, letRank);
				} else {
					throw new Missmatch();
				}
			}
			case Content.Error e -> mergeRow(u, uState, v, vState, e);
			}
		}
		case Content.Error e -> mergeRow(u, uState, v, vState, e);
		}
		return u;
	}

	/**
	 * Row1同士のlabel差分unification本体．
	 */
	private static Variable rowUnifyRow1(
			Variable u, VariableState uState, Row1 uRow,
			Variable v, VariableState vState, Row1 vRow,
			FreshFlex freshFlex, int letRank) {
		Seq<RecordField<Variable>> uFields = uRow.fields();
		Seq<RecordField<Variable>> vFields = vRow.fields();
		Optional<Variable> uTail = uRow.extension();
		Optional<Variable> vTail = vRow.extension();

		// occurs check: uがvのtailに出現する場合recursive row
		if(uTail.isPresent() && uTail.get().isSame(u)) {
			throw new Missmatch("recursive row during unification");
		}
		if(vTail.isPresent() && vTail.get().isSame(v)) {
			throw new Missmatch("recursive row during unification");
		}
		// uがvRowのtailに出現，またはvがuRowのtailに出現する場合も同様
		if(uTail.isPresent() && occursAnywhere(uTail.get(), v)) {
			throw new Missmatch("recursive row during unification");
		}
		if(vTail.isPresent() && occursAnywhere(vTail.get(), u)) {
			throw new Missmatch("recursive row during unification");
		}

		// canonical fieldsを二本indexで shared／leftOnly／rightOnly に分割．
		SeqBuffer<RecordField<Variable>> sharedU = new SeqBuffer<>();
		SeqBuffer<RecordField<Variable>> sharedV = new SeqBuffer<>();
		SeqBuffer<RecordField<Variable>> leftOnly = new SeqBuffer<>();
		SeqBuffer<RecordField<Variable>> rightOnly = new SeqBuffer<>();

		for(int i = 0; i < uFields.size(); i++) {
			RecordField<Variable> uf = uFields.at(i);
			Optional<RecordField<Variable>> vf = vFields.findFirst(
					f -> f.name().equals(uf.name()));
			if(vf.isPresent()) {
				sharedU.add(uf);
				sharedV.add(vf.get());
			} else {
				leftOnly.add(uf);
			}
		}
		for(int j = 0; j < vFields.size(); j++) {
			RecordField<Variable> vf = vFields.at(j);
			boolean found = uFields.findFirst(f -> f.name().equals(vf.name())).isPresent();
			if(!found) {
				rightOnly.add(vf);
			}
		}

		Seq<RecordField<Variable>> sharedUSeq = sharedU.toSeq();
		Seq<RecordField<Variable>> sharedVSeq = sharedV.toSeq();
		Seq<RecordField<Variable>> leftOnlySeq = leftOnly.toSeq();
		Seq<RecordField<Variable>> rightOnlySeq = rightOnly.toSeq();

		// residualのtail要件を先に検査（部分的mutation前）．
		boolean hasLeftResidual = !leftOnlySeq.isEmpty();
		boolean hasRightResidual = !rightOnlySeq.isEmpty();
		if(hasLeftResidual && vTail.isEmpty()) {
			throw new Missmatch();
		}
		if(hasRightResidual && uTail.isEmpty()) {
			throw new Missmatch();
		}

		// tailの有無だけで判定できるmismatchを除外してからshared fieldを単一化する．
		for(int i = 0; i < sharedUSeq.size(); i++) {
			unify(sharedUSeq.at(i).value(), sharedVSeq.at(i).value(), freshFlex, letRank);
		}

		// 2. residualのtail処理
		// shared fieldsの寄与は除いたresidual rowを構築してtailへunifyする．
		if(!hasLeftResidual && !hasRightResidual) {
			// residualなし
			// 両tailあり→tail同士unify
			// 片tailのみ→そのtailをROW-kind closed empty Row1へunify
			// 両tailなし→何もしない
			if(uTail.isPresent() && vTail.isPresent()) {
				rowUnify(uTail.get(), vTail.get(), freshFlex, letRank);
			} else if(uTail.isPresent() && vTail.isEmpty()) {
				// uTailをclosed empty Row1へunify
				Variable empty = makeClosedEmptyRow(uTail.get().get().rank);
				rowUnify(uTail.get(), empty, freshFlex, letRank);
			} else if(uTail.isEmpty() && vTail.isPresent()) {
				Variable empty = makeClosedEmptyRow(vTail.get().get().rank);
				rowUnify(vTail.get(), empty, freshFlex, letRank);
			} else {
				// 両方closed empty：何もしない
			}
		} else if(hasLeftResidual && !hasRightResidual) {
			// leftOnlyのみ：right tail必須（検査済み）．
			// rightTailを Row1(leftOnly, leftTail) を持つcurrent-rank ROW variableへunify．
			// leftTailなしならclosed residual．
			Optional<Variable> newTail = uTail;
			Variable residualRow = makeResidualRowRoot(letRank, leftOnlySeq, newTail);
			rowUnify(vTail.get(), residualRow, freshFlex, letRank);
		} else if(!hasLeftResidual && hasRightResidual) {
			// rightOnlyのみ：対称．
			Optional<Variable> newTail = vTail;
			Variable residualRow = makeResidualRowRoot(letRank, rightOnlySeq, newTail);
			rowUnify(uTail.get(), residualRow, freshFlex, letRank);
		} else {
			// 両側residual: 両tail必須（検査済み）．
			// current-rank fresh common ROW tailを作る．
			// leftTailを Row1(rightOnly, common) へ
			// rightTailを Row1(leftOnly, common) へ unify
			Variable common = makeFreshTail(freshFlex, letRank);
			// leftTail unify
			Variable leftResidual = makeResidualRowRoot(letRank, rightOnlySeq, Optional.of(common));
			rowUnify(uTail.get(), leftResidual, freshFlex, letRank);
			// rightTail unify
			Variable rightResidual = makeResidualRowRoot(letRank, leftOnlySeq, Optional.of(common));
			rowUnify(vTail.get(), rightResidual, freshFlex, letRank);
		}

		// 3. 全子unify成功後に元row rootsをmergeする．
		// tailへのresidual束縛によりuRowとvRowは等価になっているため，
		// 既知fieldを合成してtailにも残すのではなく，u側の正規形を採用する．
		// 合成するとresidual fieldがprefixとtailの双方へ現れ，lacks制約に違反する．
		mergeRow(u, uState, v, vState, uState.content);
		return u;
	}

	/** ROW-kind closed empty Row1を新しいVariableとして構築する． */
	private static Variable makeClosedEmptyRow(int rank) {
		return new Variable(new VariableState(
				new Structure(new Row1(Seq.of(), Optional.empty())),
				rank, Variable.Kind.ROW));
	}

	/** residual rowを新しいVariableとして構築する． */
	private static Variable makeResidualRowRoot(
			int letRank,
			Seq<RecordField<Variable>> fields,
			Optional<Variable> tail) {
		return new Variable(new VariableState(
				new Structure(new Row1(fields, tail)),
				letRank, Variable.Kind.ROW));
	}

	/** fresh common ROW tailを生成する． */
	private static Variable makeFreshTail(FreshFlex freshFlex, int letRank) {
		if(freshFlex == null) {
			throw new IllegalStateException(
					"open row unification with residuals requires FreshFlex and the current let rank");
		}
		return freshFlex.getVariable(Variable.Kind.ROW, letRank);
	}

	/** targetがrow内のextension chain（tail chain）に出現するかをoccurs check的に判定する． */
	private static boolean occursAnywhere(Variable start, Variable target) {
		Variable current = start;
		java.util.Deque<Variable> stack = new java.util.ArrayDeque<>();
		java.util.Set<Variable> visited = new HashSet<>();
		stack.push(current);
		while(!stack.isEmpty()) {
			Variable v = stack.pop();
			if(v.isSame(target)) {
				return true;
			}
			if(!visited.add(v)) {
				continue;
			}
			VariableState s = v.get();
			if(s.content instanceof Structure st && st.flatType() instanceof Row1 r) {
				r.extension().ifPresent(stack::push);
			}
		}
		return false;
	}
}

@SuppressWarnings("serial")
class Missmatch extends RuntimeException {
	Missmatch() { super(); }
	Missmatch(String message) { super(message); }
}