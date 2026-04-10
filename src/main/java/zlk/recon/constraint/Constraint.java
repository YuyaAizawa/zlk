package zlk.recon.constraint;

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
import zlk.util.collection.Seq;
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
	 *
	 * <h3>注意する箇所</h3>
	 * <li>header に現れる自由変数のうち CLet 内で新規に作ったものは flexes に入っている
	 * <li>headerCons でのみ使われる fresh も、その CLet 内で新規なら flexes に入っている
	 * <li>外から渡された expected 由来の変数は flexes に入れない
	 * <li>genTargets に入る binder だけが generalize 対象
	 *
	 */
	record CLet(
			Seq<Variable> rigids,
			Seq<Variable> flexes,
			IdMap<RcType> header,
			Seq<CPhase> headerCons,
			Seq<Constraint> bodyCon) implements Constraint {

		public CLet(
				Seq<Variable> rigids,
				Seq<Variable> flexes,
				IdMap<RcType> header,
				Seq<CPhase> headerCons,
				Constraint bodyCon) {
			this(rigids, flexes, header, headerCons, Seq.of(bodyCon));
		}
	}

	/**
	 * 一度に制約を解決すべき，依存が強連結になっている制約の集まり．
	 *
	 * @param cons 依存が強連結になっている定義の制約
	 * @param genTargets この集まりを解決したタイミングで一般化すべき対象
	 */
	record CPhase(
			Seq<Constraint> cons,
			IdList genTargets
	) implements PrettyPrintable {  // 単独でConstraintではない

		@Override
		public void mkString(PrettyPrinter pp) {
			pp.append("Phase:").endl();
			pp.indent(() -> {
				pp.append("cons: ").append(PrettyPrintable.tailComma(cons.toList())).endl();
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
			Seq<Variable> vars,
			Seq<Constraint> cons) implements Constraint {}

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
				Seq<Variable> rigids,
				Seq<Variable> flexes,
				IdMap<RcType> header,
				Seq<CPhase> headerCons,
				Seq<Constraint> bodyCons
		) -> {
			pp.append("Let:").endl();
			pp.indent(() -> {
				pp.append("rigids: ").append("[").append(PrettyPrintable.join(rigids, ", ")).append("]").endl();
				pp.append("flexes: ").append("[").append(PrettyPrintable.join(flexes, ", ")).append("]").endl();
				pp.append("header: ").append(header).endl();
				pp.append("headerCons: ").append(PrettyPrintable.tailComma(headerCons.toList())).endl();
				pp.append("bodyCons:").append(PrettyPrintable.tailComma(bodyCons.toList()));
			});
		}
		case CExists(Seq<Variable> vars, Seq<Constraint> cons) -> {
			pp.append("Exists:").endl();
			pp.indent(() -> {
				pp.append("vars: ").append("[").append(PrettyPrintable.join(vars, ", ")).append("]").endl();
				pp.append("cons: ").append(PrettyPrintable.tailComma(cons.toList()));
			});
		}
		}
	}
}
