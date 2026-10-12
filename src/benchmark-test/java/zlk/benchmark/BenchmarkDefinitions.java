package zlk.benchmark;

import java.util.List;

/** 組込み全題材の起動だけが利用する一覧．単独定義には登録不要． */
public final class BenchmarkDefinitions {
	private BenchmarkDefinitions() {}
	public static List<Class<?>> classes() {
		return List.of(RecordSingleFieldBenchmark.class, RecordMultiFieldBenchmark.class,
				RecordSharedBenchmark.class, OperandStackBenchmark.class, NamedLocalBenchmark.class, CombinedBenchmark.class);
	}
}
