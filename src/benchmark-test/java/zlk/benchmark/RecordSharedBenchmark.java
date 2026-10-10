package zlk.benchmark;

import zlk.compiler.CompilationOptions;
import zlk.compiler.CompilationOptions.Key;

@ZlkBenchmark(shape = InvocationShape.INT_TO_RECORD)
public class RecordSharedBenchmark extends ZlkBenchmarkTest {
	@Source public static String source(@Values({"2", "8", "32"}) int fields) {
		return new BenchmarkSources.Sample(BenchmarkSources.Subject.RECORD_SHARED, fields, 1, 0).source();
	}
	@Options public static CompilationOptions options(@Values({"false", "true"}) boolean reuse) {
		return CompilationOptions.DEFAULT.set(Key.OPT_REUSE_RECORD, reuse);
	}
	public static void verify(CompiledBenchmark compiled, int fields) throws Throwable {
		BenchmarkVerification.verify(compiled, new BenchmarkSources.Sample(BenchmarkSources.Subject.RECORD_SHARED, fields, 1, 0));
	}
}
