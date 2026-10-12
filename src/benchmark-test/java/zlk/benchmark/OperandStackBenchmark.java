package zlk.benchmark;

import zlk.compiler.CompilationOptions;
import zlk.compiler.CompilationOptions.Key;

@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
@JmhChart(x = "source.depth", series = "option.stack")
@ReportChart(x = "source.depth", series = {"option.stack"},
	metrics = {ReportChart.Metric.CODE_LENGTH, ReportChart.Metric.ALOAD, ReportChart.Metric.ASTORE, ReportChart.Metric.MAX_LOCALS})
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
