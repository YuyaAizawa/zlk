package zlk.recon.constraint;

import java.util.List;

import zlk.common.Type;
import zlk.common.id.Id;
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

	// TODO CPatternを独立させる

	/**
	 * 型の等式制約
	 */
	record CEqual(
			RcType type,
			RcType expected,
			Reason reason) implements Constraint {}

	/**
	 * 出現した変数の型
	 */
	record CLocal(
			Id id,
			RcType expected,
			Reason reason) implements Constraint {}

	/**
	 * 出現した外部変数（コンストラクタ含む）の型
	 */
	record CForeign(
			Id id,
			Type annotation,
			RcType expected,
			Reason reason) implements Constraint {}

	/**
	 * パターンによる制約
	 */
	record CPattern(
			Id id,  // TODO Ctor以外のカテゴリに対応
			RcType ctorTy,
			RcType expected  // TODO PReason追加
			) implements Constraint {}

	/**
	 * letやcaseのパターンとスコープに関わる制約．
	 *
	 * @param rigids 型注釈や再帰定義によって固定する型変数 単一化されない
	 * @param flexes スコープ内で導入した自由型変数 量化の候補
	 * @param header スコープ内で導入された変数と型情報の対応
	 * @param headerCons 定義部の制約
	 * @param bodyCons スコープ（letやcaseの分岐）の中で得られた本体式に対する制約
	 */
	record CLet(
			List<Variable> rigids,
			List<Variable> flexes,
			IdMap<RcType> header,
			List<Constraint> headerCons,
			List<Constraint> bodyCons) implements Constraint {}

	/**
	 * 型変数のスコープ（正確には制約ではない）
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
		case CEqual(RcType type, RcType expected, Reason reason) -> {
			pp.append(type).append(" = ").append(expected);
			if(reason != Reason.NO_EXPECTATION) {
				pp.append("  -- ").append(reason);
			}
		}
		case CLocal(Id id, RcType expected, Reason reason) -> {
			pp.append("Local: ").append(id).append(" = ").append(expected);
			if(reason != Reason.NO_EXPECTATION) {
				pp.append("  -- ").append(reason);
			}
		}
		case CForeign(Id id, Type type, RcType expected, Reason reason) -> {
			pp.append("Foreign: ").append(id).append(":").append(type).append(" = ").append(expected);
			if(reason != Reason.NO_EXPECTATION) {
				pp.append("  -- ").append(reason);
			}
		}
		case CPattern(Id id, RcType ctorTy, RcType expected) -> {
			pp.append("Pattern: ").append(id).append(":").append(ctorTy).append(" = ").append(expected);
		}
		case CLet(
				List<Variable> rigids,
				List<Variable> flexes,
				IdMap<RcType> header,
				List<Constraint> headerCons,
				List<Constraint> bodyCons
		) -> {
			pp.append("Let:").endl();
			pp.indent(() -> {
				pp.append("rigids: ").append(PrettyPrintable.from(rigids)).endl();
				pp.append("flexes: ").append(PrettyPrintable.from(flexes)).endl();
				pp.append("header: ").append(header).endl();
				pp.append("headerCons: ").append(PrettyPrintable.from(headerCons)).endl();
				pp.append("bodyCons:").append(PrettyPrintable.from(bodyCons));
			});
		}
		case CExists(List<Variable> vars, List<Constraint> cons) -> {
			pp.append("Exists: ").append(PrettyPrintable.from(vars)).endl();
			pp.indent(() -> {
				pp.append(PrettyPrintable.from(cons));
			});
		}
		}
	}
}
