package zlk.benchmark;

import zlk.compiler.CompilationOptions;
import zlk.compiler.CompilationOptions.Key;

@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
public class OperandStackBenchmark extends ZlkBenchmarkTest {
	@Source public static String source(@Values({"8", "32", "128"}) int depth) {
		return new BenchmarkSources.Sample(BenchmarkSources.Subject.OPERAND_STACK, 0, 0, depth).source();
	}
	@Options public static CompilationOptions options(@Values({"false", "true"}) boolean stack) {
		return CompilationOptions.DEFAULT.set(Key.OPT_USE_OPERAND_STACK, stack);
	}
	public static void verify(CompiledBenchmark compiled, int depth) throws Throwable {
		BenchmarkVerification.verify(compiled, new BenchmarkSources.Sample(BenchmarkSources.Subject.OPERAND_STACK, 0, 0, depth));
	}
}
