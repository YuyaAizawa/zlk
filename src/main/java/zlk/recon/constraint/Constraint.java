package zlk.recon.constraint;

import java.util.List;

import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.recon.Variable;
import zlk.recon.constraint.Constraint.CEqual;
import zlk.recon.constraint.Constraint.CExists;
import zlk.recon.constraint.Constraint.CForeign;
import zlk.recon.constraint.Constraint.CLet;
import zlk.recon.constraint.Constraint.CLocal;
import zlk.recon.constraint.Constraint.CPattern;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public sealed interface Constraint extends PrettyPrintable
permits CEqual, CLocal, CForeign, CPattern, CLet, CExists {

	// TODO: エラーメッセージ用の制約の由来
	// どんな情報が必要か詰めてから

	/**
	 * 型の等式制約
	 */
	record CEqual(
			RcType type,
			RcType expected) implements Constraint {}

	/**
	 * 出現した変数の型
	 */
	record CLocal(
			Id id,
			RcType expected) implements Constraint {}

	/**
	 * 出現した外部変数（コンストラクタ含む）の型
	 */
	record CForeign(
			Id id,
			Type ty,
			RcType expected) implements Constraint {}

	/**
	 * パターンによる制約
	 */
	record CPattern(
			Id id,  // TODO Ctor以外のカテゴリに対応
			RcType ctorTy,
			RcType expected) implements Constraint {}

	/**
	 * letやcaseのパターンとスコープに関わる制約．
	 *
	 * @param rigids 型注釈や再帰定義によって固定する型変数 単一化されない
	 * @param flexes スコープ内で導入した自由型変数 量化の候補
	 * @param header スコープ内で導入された変数と型情報の対応
	 * @param headerCons 定義部の制約(要素は強連結成分ごとで順序は依存関係を反映)
	 * @param bodyCons スコープ（letやcaseの分岐）の中で得られた本体式に対する制約
	 */
	record CLet(
			List<Variable> rigids,
			List<Variable> flexes,
			IdMap<RcType> header,
			List<CPhase> headerCons,
			List<Constraint> bodyCon) implements Constraint {

		public CLet(
				List<Variable> rigids,
				List<Variable> flexes,
				IdMap<RcType> header,
				List<CPhase> headerCons,
				Constraint bodyCon) {
			this(rigids, flexes, header, headerCons, List.of(bodyCon));
		}
	}

	/**
	 * 一度に制約を解決すべき，依存が強連結になっている制約の集まり．
	 *
	 * @param cons 依存が強連結になっている定義の制約
	 * @param genTargets この集まりを解決したタイミングで一般化すべき対象
	 */
	record CPhase(
		List<Constraint> cons,
		IdList genTargets) implements PrettyPrintable {  // 単独でConstraintではない

		@Override
		public void mkString(PrettyPrinter pp) {
			pp.append("Phase:").endl();
			pp.indent(() -> {
				pp.append("cons: ").append(PrettyPrintable.tailComma(cons)).endl();
				pp.append("genTargets: ").append(genTargets);
			});
		}
	}

	/**
	 * 型変数のスコープ（正確には制約ではない）．
	 * 関数の引数やcaseのパターンと戻り値など，
	 * 導入した型変数が外と繋がらないことを確定させるのに使う．
	 *
	 * @param vars 型変数
	 * @param cons 制約
	 */
	record CExists(
			List<Variable> vars,
			List<Constraint> cons) implements Constraint {}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch (this) {
		case CEqual(RcType type, RcType expected) -> {
			pp.append(type).append(" = ").append(expected);
		}
		case CLocal(Id id, RcType expected) -> {
			pp.append("Local: ").append(id).append(" = ").append(expected);
		}
		case CForeign(Id id, Type type, RcType expected) -> {
			pp.append("Foreign: ").append(id).append(":").append(type).append(" = ").append(expected);
		}
		case CPattern(Id id, RcType ctorTy, RcType expected) -> {
			pp.append("Pattern: ").append(id).append(": ").append(ctorTy).append(" = ").append(expected);
		}
		case CLet(
				List<Variable> rigids,
				List<Variable> flexes,
				IdMap<RcType> header,
				List<CPhase> headerCons,
				List<Constraint> bodyCons
		) -> {
			pp.append("Let:").endl();
			pp.indent(() -> {
				pp.append("rigids: ").append("[").append(PrettyPrintable.join(rigids, ", ")).append("]").endl();
				pp.append("flexes: ").append("[").append(PrettyPrintable.join(flexes, ", ")).append("]").endl();
				pp.append("header: ").append(header).endl();
				pp.append("headerCons: ").append(PrettyPrintable.tailComma(headerCons)).endl();
				pp.append("bodyCons:").append(PrettyPrintable.tailComma(bodyCons));
			});
		}
		case CExists(List<Variable> vars, List<Constraint> cons) -> {
			pp.append("Exists:").endl();
			pp.indent(() -> {
				pp.append("vars: ").append("[").append(PrettyPrintable.join(vars, ", ")).append("]").endl();
				pp.append("cons: ").append(PrettyPrintable.tailComma(cons));
			});
		}
		}
	}
}
