package zlk.benchmark;

import zlk.compiler.CompilationOptions;
import zlk.compiler.CompilationOptions.Key;

@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
@JmhChart(x = "option.stack")
@ReportChart(x = "option.stack", plot = ReportChart.Plot.BAR,
	metrics = {ReportChart.Metric.CODE_LENGTH, ReportChart.Metric.ALOAD, ReportChart.Metric.ASTORE, ReportChart.Metric.MAX_LOCALS})
public class NamedLocalBenchmark extends ZlkBenchmarkTest {
	@Source public static String source() {
		return new BenchmarkSources.Sample(BenchmarkSources.Subject.NAMED_LOCAL, 0, 0, 0).source();
	}
	@Options public static CompilationOptions options(@Values({"false", "true"}) boolean stack) {
		return CompilationOptions.DEFAULT.set(Key.OPT_USE_OPERAND_STACK, stack);
	}
	public static void verify(CompiledBenchmark compiled) throws Throwable {
		BenchmarkVerification.verify(compiled, new BenchmarkSources.Sample(BenchmarkSources.Subject.NAMED_LOCAL, 0, 0, 0));
	}
}
