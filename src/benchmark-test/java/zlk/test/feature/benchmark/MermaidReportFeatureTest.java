package zlk.test.feature.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zlk.benchmark.BenchmarkDefinition;
import zlk.benchmark.BenchmarkMain;
import zlk.benchmark.BytecodeMetrics;
import zlk.benchmark.BytecodeReport;
import zlk.benchmark.BytecodeReport.Measurement;
import zlk.benchmark.CompiledBenchmark;
import zlk.benchmark.ForkDefinitionFixture;
import zlk.benchmark.InvocationShape;
import zlk.benchmark.MermaidReport;
import zlk.benchmark.Options;
import zlk.benchmark.ReportChart;
import zlk.benchmark.Source;
import zlk.benchmark.Values;
import zlk.benchmark.ZlkBenchmark;
import zlk.compiler.CompilationOptions;

public class MermaidReportFeatureTest {
	@TempDir Path output;

	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	@ReportChart(x = "source.updates", series = {"option.reuse", "option.stack"},
			metrics = {ReportChart.Metric.CODE_LENGTH, ReportChart.Metric.ALOAD})
	@ReportChart(x = "source.updates", series = {"source.fields", "option.reuse", "option.stack"}, namedSeries = true)
	public static class Matrix {
		@Source public static String source(@Values({"8", "2"}) int fields, @Values({"032", "1", "8"}) int updates) {
			return "module Bench\nbench x = add x " + (fields + updates) + "\n";
		}
		@Options public static CompilationOptions options(@Values({"false", "true"}) boolean reuse, @Values({"false", "true"}) boolean stack) {
			return CompilationOptions.DEFAULT.set(CompilationOptions.Key.OPT_REUSE_RECORD, reuse).set(CompilationOptions.Key.OPT_USE_OPERAND_STACK, stack);
		}
		public static void verify(CompiledBenchmark c, int fields, int updates) throws Throwable {
			assertEquals(17 + fields + updates, (Integer)c.handle().invokeExact(Integer.valueOf(17)));
		}
	}
	private static List<Measurement> measurements(BenchmarkDefinition definition) {
		return definition.cases().stream().map(item -> {
			var params = definition.parameters(item);
			int value = Integer.parseInt(params.getOrDefault("source.fields", "0")) * 100
					+ Integer.parseInt(params.getOrDefault("source.updates", "0"))
					+ (params.getOrDefault("option.reuse", "false").equals("true") ? 10 : 0)
					+ (params.getOrDefault("option.stack", "false").equals("true") ? 1000 : 0);
			return new Measurement(item, params, 2000, 1900, 1, new BytecodeMetrics(value, 0, 0, 0, 0, 0, 0));
		}).toList();
	}
	private static String render(Class<?> type) {
		var definition = BenchmarkDefinition.of(type);
		return MermaidReport.render(List.of(definition), measurements(definition));
	}
	@Test void alignsMultipleSeriesSortsNumericXAndSeparatesUnselectedConditions() {
		String md = render(Matrix.class);
		assertEquals(5, md.split("```mermaid", -1).length - 1);
		assertTrue(md.contains("title \"code_length / source.fields=8\""));
		assertTrue(md.contains("title \"code_length / source.fields=2\""));
		assertTrue(md.contains("x-axis \"source.updates\" [\"1\", \"8\", \"32\"]"));
		assertTrue(md.contains("line [801, 808, 832]\n    line [1801, 1808, 1832]\n    line [811, 818, 842]\n    line [1811, 1818, 1842]"));
		assertTrue(md.contains("| 1 | option.reuse=false; option.stack=false | 801 | 808 | 832 |"));
		assertTrue(md.contains("line \"source.fields=2; option.reuse=true; option.stack=true\" [1211, 1218, 1242]"));
		assertEquals(md, render(Matrix.class));
	}
	@Test void rejectsDuplicateOrMissingPointsRatherThanAggregatingOrFillingWithZero() {
		var definition = BenchmarkDefinition.of(Matrix.class);
		var rows = new ArrayList<>(measurements(definition));
		rows.add(rows.getFirst());
		assertTrue(assertThrows(IllegalArgumentException.class, () -> MermaidReport.render(List.of(definition), rows))
				.getMessage().contains("duplicate chart point"));
		rows.removeLast(); rows.removeFirst();
		assertTrue(assertThrows(IllegalArgumentException.class, () -> MermaidReport.render(List.of(definition), rows))
				.getMessage().contains("missing chart point"));
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	@ReportChart(x = "option.stack", plot = ReportChart.Plot.BAR, metrics = {ReportChart.Metric.ALOAD})
	public static class Bars {
		@Source public static String source() { return "module Bench\nbench x = x\n"; }
		@Options public static CompilationOptions options(@Values({"true", "false"}) boolean stack) { return CompilationOptions.DEFAULT; }
		public static void verify(CompiledBenchmark c) {}
	}
	@Test void supportsBooleanCategoriesBarsAndAllZeroMetrics() {
		String md = render(Bars.class);
		assertTrue(md.contains("x-axis \"option.stack\" [\"false\", \"true\"]"));
		assertTrue(md.contains("y-axis \"aload\" 0 --> 1\n    bar [0, 0]"));
		assertFalse(md.contains("line ["));
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	@ReportChart(x = "source.missing")
	public static class UnknownX extends Bars { @Source public static String source() { return Bars.source(); } }
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	@ReportChart(x = "option.stack", series = {"option.stack"})
	public static class DuplicateDimension extends Bars {
		@Source public static String source() { return Bars.source(); }
		@Options public static CompilationOptions options(@Values({"false", "true"}) boolean stack) { return CompilationOptions.DEFAULT; }
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	@ReportChart(x = "option.stack", metrics = {})
	public static class EmptyMetrics extends DuplicateDimension {
		@Source public static String source() { return Bars.source(); }
		@Options public static CompilationOptions options(@Values({"false", "true"}) boolean stack) { return CompilationOptions.DEFAULT; }
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	@ReportChart(x = "source.updates", series = {"option.reuse", "option.stack"}, plot = ReportChart.Plot.BAR)
	public static class OverlaidBars extends Matrix {
		@Source public static String source(@Values({"8"}) int fields, @Values({"1", "8"}) int updates) { return Matrix.source(fields, updates); }
		@Options public static CompilationOptions options(@Values({"false", "true"}) boolean reuse, @Values({"false", "true"}) boolean stack) { return Matrix.options(reuse, stack); }
	}
	@Test void rejectsInvalidChartDefinitions() {
		for(Class<?> type : List.of(UnknownX.class, DuplicateDimension.class, EmptyMetrics.class, OverlaidBars.class))
			assertThrows(IllegalArgumentException.class, () -> render(type), type.getName());
	}
	@Test void unannotatedDefinitionsStillProduceCsvAndAnExplanation() throws Exception {
		BytecodeReport.write(output.resolve("bytecode.csv"), null, List.of(ForkDefinitionFixture.class));
		assertEquals(2, Files.readAllLines(output.resolve("bytecode.csv")).size());
		assertTrue(Files.readString(output.resolve("report.md")).contains("`@ReportChart` が未指定"));
	}
	@Test void reportsAllBuiltinsAndUsesTheSameValuesAsCsv() throws Exception {
		BenchmarkMain.main(new String[] {"report", "all", output.toString()});
		Path report;
		try(var directories = Files.list(output)) { report = directories.findFirst().orElseThrow(); }
		String md = Files.readString(report.resolve("report.md"));
		var lines = Files.readAllLines(report.resolve("bytecode.csv"));
		assertEquals(61, lines.size());
		for(String definition : List.of("RecordSingleField", "RecordMultiField", "RecordShared", "OperandStack", "NamedLocal", "Combined"))
			assertTrue(md.contains("## zlk.benchmark." + definition + "Benchmark"));
		List<String> values = new ArrayList<>();
		for(String line : lines.subList(1, lines.size())) {
			String[] cells = line.split(",");
			if(cells[0].equals("zlk.benchmark.RecordSingleFieldBenchmark") && cells[1].startsWith("fields=2;") && cells[2].equals("reuse=false"))
				values.add(cells[11]);
		}
		assertTrue(md.contains("line [" + String.join(", ", values) + "]"));
		assertTrue(Files.exists(report.resolve("classes/case-59/Bench.class")));
	}
}
