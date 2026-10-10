package zlk.benchmark;

import zlk.compiler.CompilationOptions;
import zlk.compiler.CompilationOptions.Key;

@ZlkBenchmark(shape = InvocationShape.INT_TO_RECORD)
public class CombinedBenchmark extends ZlkBenchmarkTest {
	@Source public static String source(@Values({"8", "32"}) int fields, @Values({"8", "32"}) int updates) {
		return new BenchmarkSources.Sample(BenchmarkSources.Subject.COMBINED, fields, updates, 0).source();
	}
	@Options public static CompilationOptions options(@Values({"false", "true"}) boolean reuse, @Values({"false", "true"}) boolean stack) {
		return CompilationOptions.DEFAULT.set(Key.OPT_REUSE_RECORD, reuse).set(Key.OPT_USE_OPERAND_STACK, stack);
	}
	public static void verify(CompiledBenchmark compiled, int fields, int updates) throws Throwable {
		BenchmarkVerification.verify(compiled, new BenchmarkSources.Sample(BenchmarkSources.Subject.COMBINED, fields, updates, 0));
	}
}
