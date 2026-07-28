package zlk.recon;

import java.util.function.Function;

import zlk.common.Location;
import zlk.recon.constraint.RcType;
import zlk.util.collection.Seq;

/**
 * 一つのcase式に含まれるbranch patternの型付き木．
 *
 * @param loc case式全体のlocation
 * @param patterns branchのsource orderに並んだ型付きpattern
 * @param <T> 制約抽出時は{@link RcType}，型再構築後は解決済み型
 */
public record CaseTyping<T>(
		Location loc,
		Seq<PatternTyping<T>> patterns) {

	public <U> CaseTyping<U> map(Function<? super T, ? extends U> mapper) {
		return new CaseTyping<>(loc, patterns.map(pattern -> pattern.map(mapper)));
	}
}
