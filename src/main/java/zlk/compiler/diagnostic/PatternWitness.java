package zlk.compiler.diagnostic;

import zlk.util.collection.Seq;

/**
 * 網羅されていないパターンの例
 */
public sealed interface PatternWitness {

	enum Anything implements PatternWitness {
		SINGLETON
	}

	record Ctor(
			String name,
			Seq<PatternWitness> args
	) implements PatternWitness {}

}
