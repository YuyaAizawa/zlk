package zlk.ir.typing;

import java.util.function.Function;

import zlk.ir.idcalc.IcPattern;
import zlk.phase.recon.constraint.RcType;
import zlk.util.collection.Seq;

/**
 * pattern構文と，制約抽出時に割り当てた型を対応付けた木．
 *
 * @param pattern 対応する名前解決済みpattern
 * @param type pattern全体へ割り当てた型
 * @param children patternの子と同じ順序の型付きpattern
 * @param <T> 制約抽出時は{@link RcType}，型再構築後は解決済み型
 */
public record PatternTyping<T>(
		IcPattern pattern,
		T type,
		Seq<PatternTyping<T>> children) {

	public PatternTyping {
		int expectedChildren = switch(pattern) {
		case IcPattern.Wildcard _, IcPattern.Var _ -> 0;
		case IcPattern.Dector(_, Seq<IcPattern.Arg> args, _) -> args.size();
		case IcPattern.Record(Seq<IcPattern.RecordField> fields, _) -> fields.size();
		};
		if(children.size() != expectedChildren) {
			throw new IllegalArgumentException(
					"pattern child count mismatch: " + expectedChildren + " vs " + children.size());
		}
	}

	public <U> PatternTyping<U> map(Function<? super T, ? extends U> mapper) {
		return new PatternTyping<>(
				pattern,
				mapper.apply(type),
				children.map(child -> child.map(mapper)));
	}
}
