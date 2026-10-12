package zlk.test.feature.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import zlk.benchmark.BenchmarkDefinition;
import zlk.benchmark.BenchmarkMain;
import zlk.benchmark.BenchmarkMetadata;
import zlk.benchmark.BenchmarkSources;
import zlk.benchmark.CompiledBenchmark;
import zlk.benchmark.ForkDefinitionFixture;
import zlk.benchmark.IntToIntJmhBenchmark;
import zlk.benchmark.IntToRecordJmhBenchmark;
import zlk.benchmark.InvocationShape;
import zlk.benchmark.Options;
import zlk.benchmark.Source;
import zlk.benchmark.Values;
import zlk.benchmark.ZlkBenchmark;
import zlk.benchmark.ZlkBenchmarkRunner;
import zlk.benchmark.ZlkJmhState;
import zlk.compiler.CompilationOptions;

public class BenchmarkDefinitionFeatureTest {
	@ZlkBenchmark(shape = InvocationShape.INT_TO_RECORD, gc = false)
	@zlk.benchmark.JmhChart(x = "source.updates", series = "option.reuse")
	public static class ColoredRecord {
		@Source public static String source(@Values({"8"}) int fields, @Values({"32", "1", "8"}) int updates) {
			return zlk.benchmark.RecordMultiFieldBenchmark.source(fields, updates);
		}
		@Options public static CompilationOptions options(@Values({"true", "false"}) boolean reuse, @Values({"false", "true"}) boolean stack) {
			return CompilationOptions.DEFAULT.set(CompilationOptions.Key.OPT_REUSE_RECORD, reuse).set(CompilationOptions.Key.OPT_USE_OPERAND_STACK, stack);
		}
		public static void verify(CompiledBenchmark compiled, int fields, int updates) throws Throwable {
			zlk.benchmark.RecordMultiFieldBenchmark.verify(compiled, fields, updates);
		}
	}
	@Test void recordRunComparesColoredReuseBarsAcrossSortedUpdateCounts() throws Exception {
		var output = java.nio.file.Path.of("target/benchmarks/colored-record-fixture", java.util.UUID.randomUUID().toString());
		var results = ZlkBenchmarkRunner.smoke(ColoredRecord.class, output);
		assertEquals(12, results.size());
		String md = java.nio.file.Files.readString(output.resolve("report.md"));
		assertEquals(2, md.split("```mermaid", -1).length - 1);
		assertTrue(md.contains("## source.fields=8; option.stack=false"));
		assertTrue(md.contains("## source.fields=8; option.stack=true"));
		assertTrue(md.contains("plotColorPalette: '#D55E00, #0072B2'"));
		assertTrue(md.contains("x-axis \"source.updates\" [\"1 / false\", \"1 / true\", \"8 / false\", \"8 / true\", \"32 / false\", \"32 / true\"]"));
		assertTrue(md.contains("option.reuse=false：橙，option.reuse=true：青"));
		assertTrue(md.contains(".bar-plot-0 rect:nth-child(odd)"));
		assertTrue(md.contains(".bar-plot-0 rect:nth-child(even)"));
		assertEquals(2, md.split("    bar \\[", -1).length - 1);
		assertTrue(!md.contains("line ["));
		for(boolean stack : new boolean[] {false, true}) {
			var section = md.split("## source.fields=8; option.stack=" + stack + "\\n", 2)[1].split("\\n## ", 2)[0];
			var scores = new java.util.ArrayList<String>();
			for(int updates : new int[] {1, 8, 32}) {
				for(boolean reuse : new boolean[] {false, true}) {
					var result = results.stream().filter(r -> r.getParams().getParam("sourceCase").equals("fields=8;updates=" + updates)
							&& r.getParams().getParam("optionCase").equals("reuse=" + reuse + ";stack=" + stack)).findFirst().orElseThrow();
					String score = java.math.BigDecimal.valueOf(result.getPrimaryResult().getScore()).toPlainString();
					assertTrue(section.contains("| " + updates + " | " + reuse + " | " + score + " |"));
					scores.add(score);
				}
			}
			assertTrue(section.contains("bar [" + String.join(", ", scores) + "]"));
		}
	}
	@Test void rejectsMissingParameterNamesRatherThanInventingArgZero() {
		var cw = new org.objectweb.asm.ClassWriter(0);
		cw.visit(org.objectweb.asm.Opcodes.V23, org.objectweb.asm.Opcodes.ACC_PUBLIC, "zlk/benchmark/UnnamedDefinition", null, "java/lang/Object", null);
		var annotation = cw.visitAnnotation("Lzlk/benchmark/ZlkBenchmark;", true);
		annotation.visitEnum("shape", "Lzlk/benchmark/InvocationShape;", "INT_TO_INT"); annotation.visitEnd();
		var source = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_STATIC, "source", "(I)Ljava/lang/String;", null, null);
		source.visitAnnotation("Lzlk/benchmark/Source;", true).visitEnd();
		var values = source.visitParameterAnnotation(0, "Lzlk/benchmark/Values;", true).visitArray("value");
		values.visit(null, "1"); values.visitEnd();
		source.visitCode(); source.visitLdcInsn("module Bench\nbench x = add x 1\n"); source.visitInsn(org.objectweb.asm.Opcodes.ARETURN); source.visitMaxs(1, 1); source.visitEnd();
		var verify = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_STATIC, "verify", "(Lzlk/benchmark/CompiledBenchmark;I)V", null, null);
		verify.visitCode(); verify.visitInsn(org.objectweb.asm.Opcodes.RETURN); verify.visitMaxs(0, 2); verify.visitEnd(); cw.visitEnd();
		byte[] bytes = cw.toByteArray();
		Class<?> type = new ClassLoader(getClass().getClassLoader()) {
			Class<?> define() { return defineClass("zlk.benchmark.UnnamedDefinition", bytes, 0, bytes.length); }
		}.define();
		var failure = assertThrows(IllegalArgumentException.class, () -> BenchmarkDefinition.of(type));
		assertTrue(failure.getMessage().contains("named parameter"));
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	public static class NullOptions extends ForkDefinitionFixture {
		@Source public static String source() { return ForkDefinitionFixture.source(); }
		@Options public static CompilationOptions options() { return null; }
	}
	@Test void rejectsNullOptionsAndUnknownOptionCases() {
		assertThrows(IllegalArgumentException.class, () -> BenchmarkDefinition.of(NullOptions.class).cases());
		assertThrows(IllegalArgumentException.class, () -> BenchmarkDefinition.of(Matrix.class).resolve("count=1;doubled=false", "stack=1;reuse=false"));
		assertEquals("\"" + "\\" + "t" + "\"", BenchmarkMetadata.quote(String.valueOf((char) 9)));
		assertEquals("\"" + "\\" + "u0001" + "\"", BenchmarkMetadata.quote(String.valueOf((char) 1)));
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	public static class UnstableSource {
		private static int counter;
		@Source public static String source() { return "module Bench\nbench x = add x " + counter++ + "\n"; }
		@Options public static CompilationOptions options(@Values({"false", "true"}) boolean stack) { return CompilationOptions.DEFAULT; }
		public static void verify(CompiledBenchmark c) {}
	}
	@Test void rejectsSourceChangesAcrossOptionCases() {
		var failure = assertThrows(IllegalArgumentException.class, () -> BenchmarkDefinition.of(UnstableSource.class).cases());
		assertTrue(failure.getMessage().contains("source"));
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT, forks = 0)
	public static class InvalidForks extends ForkDefinitionFixture {
		@Source public static String source() { return ForkDefinitionFixture.source(); }
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	public static class NoValues {
		@Source public static String source(int x) { return ""; }
		public static void verify(CompiledBenchmark c, int x) {}
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	public static class EmptyValues {
		@Source public static String source(@Values({}) int x) { return ""; }
		public static void verify(CompiledBenchmark c, int x) {}
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	public static class UnsupportedValues {
		@Source public static String source(@Values({"1"}) String x) { return ""; }
		public static void verify(CompiledBenchmark c, String x) {}
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	public static class OverflowValues {
		@Source public static String source(@Values({"2147483648"}) int x) { return ""; }
		public static void verify(CompiledBenchmark c, int x) {}
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	public static class PrivateSource {
		@Source private static String source() { return ""; }
		public static void verify(CompiledBenchmark c) {}
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	public static class InstanceSource {
		@Source public String source() { return ""; }
		public static void verify(CompiledBenchmark c) {}
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	public static class WrongReturn {
		@Source public static int source() { return 1; }
		public static void verify(CompiledBenchmark c) {}
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	public static class DuplicateOptions extends ForkDefinitionFixture {
		@Source public static String source() { return ForkDefinitionFixture.source(); }
		@Options public static CompilationOptions one() { return CompilationOptions.DEFAULT; }
		@Options public static CompilationOptions two() { return CompilationOptions.DEFAULT; }
	}
	@Test void rejectsInvalidInputContractsAndNonforkedMeasurements() {
		for(Class<?> invalid : new Class<?>[] {NoValues.class, EmptyValues.class, UnsupportedValues.class, OverflowValues.class,
				PrivateSource.class, InstanceSource.class, WrongReturn.class, DuplicateOptions.class, InvalidForks.class})
			assertThrows(IllegalArgumentException.class, () -> BenchmarkDefinition.of(invalid), invalid.getName());
	}
	@Test void repeatedCliReportsPreserveEarlierCsvAndDumps() throws Exception {
		var root = java.nio.file.Path.of("target/benchmarks/report-history", java.util.UUID.randomUUID().toString());
		java.nio.file.Files.createDirectories(root);
		var legacy = root.resolve("bytecode.csv");
		java.nio.file.Files.writeString(legacy, "existing report");
		BenchmarkMain.main(new String[] {"report", CustomSettings.class.getName(), root.toString()});
		assertEquals("existing report", java.nio.file.Files.readString(legacy));
		java.nio.file.Path first;
		try(var directories = java.nio.file.Files.list(root)) {
			var reports = directories.filter(java.nio.file.Files::isDirectory).toList();
			assertEquals(1, reports.size()); first = reports.getFirst();
		}
		var csv = first.resolve("bytecode.csv");
		var dump = first.resolve("classes/case-0/Bench.zlk");
		var markdown = first.resolve("report.md");
		java.nio.file.Files.writeString(markdown, "saved markdown");
		java.nio.file.Files.writeString(csv, "saved csv");
		java.nio.file.Files.writeString(dump, "saved source");
		BenchmarkMain.main(new String[] {"report", CustomSettings.class.getName(), root.toString()});
		assertEquals("saved csv", java.nio.file.Files.readString(csv));
		assertEquals("saved source", java.nio.file.Files.readString(dump));
		assertEquals("saved markdown", java.nio.file.Files.readString(markdown));
		try(var directories = java.nio.file.Files.list(root)) {
			var reports = directories.filter(java.nio.file.Files::isDirectory).toList();
			assertEquals(2, reports.size());
			var second = reports.stream().filter(p -> !p.equals(first)).findFirst().orElseThrow();
			assertEquals(2, java.nio.file.Files.readAllLines(second.resolve("bytecode.csv")).size());
			assertEquals(CustomSettings.source(), java.nio.file.Files.readString(second.resolve("classes/case-0/Bench.zlk")));
			assertTrue(java.nio.file.Files.exists(second.resolve("classes/case-0/Bench.class")));
			assertTrue(java.nio.file.Files.readString(second.resolve("report.md")).contains("# Bytecode report"));
		}
	}
	@Test void cliRunsUnregisteredTestDefinitionAndReportsJoinKeys() throws Exception {
		var root = java.nio.file.Path.of("target/benchmarks/cli-fixture", java.util.UUID.randomUUID().toString());
		BenchmarkMain.main(new String[] {"report", CustomSettings.class.getName(), root.toString()});
		java.nio.file.Path report;
		try(var directories = java.nio.file.Files.list(root)) {
			var reports = directories.filter(java.nio.file.Files::isDirectory).toList();
			assertEquals(1, reports.size());
			report = reports.getFirst();
		}
		var lines = java.nio.file.Files.readAllLines(report.resolve("bytecode.csv"));
		assertEquals(2, lines.size()); assertTrue(lines.get(1).startsWith(CustomSettings.class.getName() + ",fixed,fixed,"));
		BenchmarkMain.main(new String[] {"smoke", CustomSettings.class.getName(), root.toString(), "-Xint"});
		try(var files = java.nio.file.Files.walk(root)) {
			var resultFiles = files.filter(p -> p.getFileName().toString().equals("results.json")).toList();
			assertEquals(1, resultFiles.size());
			String json = java.nio.file.Files.readString(resultFiles.getFirst());
			assertTrue(json.contains("IntToRecordJmhBenchmark.run")); assertTrue(json.contains("-Xint"));
			String markdown = java.nio.file.Files.readString(resultFiles.getFirst().resolveSibling("report.md"));
			assertTrue(markdown.contains("# JMH report"));
			assertTrue(markdown.contains("smoke：true"));
			assertTrue(markdown.contains("-Xint"));
			assertTrue(markdown.contains("bar ["));
			assertTrue(markdown.contains("ns/op"));
			assertTrue(markdown.contains("GC profilerが無効"));
		}
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_RECORD, gc = false, forks = 2, warmupIterations = 3, measurementIterations = 4, seconds = 2)
	public static class CustomSettings {
		@Source public static String source() { return "module Bench\nbench x = { value = add x 1 }\n"; }
		public static void verify(CompiledBenchmark c) throws Throwable {
			zlk.runtime.ZlkRecord result = (zlk.runtime.ZlkRecord) c.handle().invokeExact(Integer.valueOf(17));
			assertEquals(18, result.get("value"));
		}
	}
	@Test void runnerSelectsExactShapeAndCentralizesSettings() {
		var defaults = ZlkBenchmarkRunner.options(ForkDefinitionFixture.class, java.nio.file.Path.of("target/benchmarks/settings"), false);
		assertEquals(3, defaults.getForkCount().get());
		assertEquals(5, defaults.getWarmupIterations().get());
		assertEquals(5, defaults.getMeasurementIterations().get());
		assertEquals(1, defaults.getThreads().get());
		assertEquals(java.util.Set.of(org.openjdk.jmh.annotations.Mode.AverageTime), java.util.Set.copyOf(defaults.getBenchModes()));
		assertEquals(java.util.concurrent.TimeUnit.NANOSECONDS, defaults.getTimeUnit().get());
		assertEquals(1, defaults.getProfilers().size());
		var custom = ZlkBenchmarkRunner.options(CustomSettings.class, java.nio.file.Path.of("target/benchmarks/settings"), false);
		assertEquals(java.util.List.of("^\\Qzlk.benchmark.IntToRecordJmhBenchmark.run\\E$"), custom.getIncludes());
		assertEquals(2, custom.getForkCount().get()); assertEquals(3, custom.getWarmupIterations().get());
		assertEquals(4, custom.getMeasurementIterations().get()); assertEquals(org.openjdk.jmh.runner.options.TimeValue.seconds(2), custom.getMeasurementTime().get());
		assertTrue(custom.getProfilers().isEmpty());
		var smoke = ZlkBenchmarkRunner.options(Matrix.class, java.nio.file.Path.of("target/benchmarks/settings"), true, "-Xint");
		assertEquals(1, smoke.getForkCount().get()); assertEquals(4, smoke.getParameter("sourceCase").get().size());
		assertEquals(4, smoke.getParameter("optionCase").get().size()); assertTrue(smoke.getJvmArgsAppend().get().contains("-Xint"));
		assertTrue(smoke.shouldFailOnError().get());
	}
	@Test void builtInDefinitionsPreserveSourcesAndOnlyVaryTargetOptions() throws Throwable {
		int count = 0;
		for(var subject : BenchmarkSources.Subject.values()) {
			Class<?> type = Class.forName("zlk.benchmark." + subject.benchmarkName());
			var definition = BenchmarkDefinition.of(type);
			assertNotNull(definition.jmhChart());
			assertEquals(switch(subject) {
				case RECORD_SINGLE_FIELD, RECORD_MULTI_FIELD, COMBINED -> "source.updates";
				case RECORD_SHARED -> "source.fields";
				case OPERAND_STACK -> "source.depth";
				case NAMED_LOCAL -> "option.stack";
			}, definition.jmhChart().x());
			assertEquals(switch(subject) {
				case RECORD_SINGLE_FIELD, RECORD_MULTI_FIELD, RECORD_SHARED, COMBINED -> "option.reuse";
				case OPERAND_STACK -> "option.stack";
				case NAMED_LOCAL -> "";
			}, definition.jmhChart().series());
			for(var item : definition.cases()) {
				var args = new java.util.HashMap<String, Integer>();
				if(!item.sourceCase().equals("fixed")) for(String part : item.sourceCase().split(";")) {
					String[] pair = part.split("="); args.put(pair[0], Integer.valueOf(pair[1]));
				}
				var sample = new BenchmarkSources.Sample(subject, args.getOrDefault("fields", 0),
						args.getOrDefault("updates", subject == BenchmarkSources.Subject.RECORD_SHARED ? 1 : 0), args.getOrDefault("depth", 0));
				assertEquals(sample.source(), item.source());
				for(var key : CompilationOptions.Key.values()) {
					boolean targeted = key == CompilationOptions.Key.OPT_REUSE_RECORD && sample.fields() != 0
							|| key == CompilationOptions.Key.OPT_USE_OPERAND_STACK && (sample.fields() == 0 || subject == BenchmarkSources.Subject.COMBINED);
					if(!targeted) assertEquals(CompilationOptions.DEFAULT.isEnabled(key), item.options().isEnabled(key));
				}
				var state = new ZlkJmhState(); state.definitionClass = item.definitionClass(); state.sourceCase = item.sourceCase(); state.optionCase = item.optionCase();
				state.setup();
				if(sample.fields() == 0) assertNotNull(new IntToIntJmhBenchmark().run(state));
				else assertNotNull(new IntToRecordJmhBenchmark().run(state));
				count++;
			}
		}
		assertEquals(60, count);
	}
	@Test void namedLocalRunUsesBooleanXAxisWithoutArtificialSourceParameters() throws Exception {
		var output = java.nio.file.Path.of("target/benchmarks/named-local-chart-fixture", java.util.UUID.randomUUID().toString());
		var results = ZlkBenchmarkRunner.smoke(zlk.benchmark.NamedLocalBenchmark.class, output);
		assertEquals(2, results.size());
		String md = java.nio.file.Files.readString(output.resolve("report.md"));
		assertEquals(2, md.split("```mermaid", -1).length - 1);
		assertTrue(md.contains("x-axis \"option.stack\" [\"false\", \"true\"]"));
		assertTrue(md.contains("option.stack=false：橙，option.stack=true：青"));
		assertTrue(md.contains("| option.stack | score (ns/op) |"));
		assertTrue(!md.contains("| option.stack |  |"));
		assertTrue(!md.contains("false / false"));
		for(String metric : java.util.List.of("primary", "gc.alloc.rate.norm")) {
			var scores = new java.util.ArrayList<String>();
			for(String stack : java.util.List.of("false", "true")) {
				var result = results.stream().filter(r -> r.getParams().getParam("optionCase").equals("stack=" + stack)).findFirst().orElseThrow();
				assertEquals("fixed", result.getParams().getParam("sourceCase"));
				var value = metric.equals("primary") ? result.getPrimaryResult() : result.getSecondaryResults().get(metric);
				String score = java.math.BigDecimal.valueOf(value.getScore()).toPlainString();
				scores.add(score);
				assertTrue(md.contains("| " + stack + " | " + score + " |"));
			}
			assertTrue(md.contains("bar [" + String.join(", ", scores) + "]"));
		}
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	public static class WrongAbi {
		@Source public static String source() { return "module Bench\nbench x = { value = x }\n"; }
		public static void verify(CompiledBenchmark c) { fail("ABI must be rejected before verify"); }
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	public static class FailedVerify {
		@Source public static String source(@Values({"2"}) int count) { return "module Bench\nbench x = add x 1\n"; }
		public static void verify(CompiledBenchmark c, int count) { throw new IllegalStateException("verify cause " + count); }
	}
	@Test void trialRejectsAbiAndVerificationFailuresBeforeInvocation() {
		for(Class<?> fixture : new Class<?>[] {WrongAbi.class, FailedVerify.class}) {
			var item = BenchmarkDefinition.of(fixture).cases().getFirst();
			var state = new ZlkJmhState();
			state.definitionClass = item.definitionClass(); state.sourceCase = item.sourceCase(); state.optionCase = item.optionCase();
			var failure = assertThrows(IllegalArgumentException.class, state::setup);
			if(fixture == WrongAbi.class) assertTrue(failure.getMessage().contains("ABI"));
			else { assertEquals("verify cause 2", failure.getCause().getMessage()); assertTrue(failure.getMessage().contains("count=2")); }
		}
	}
	@Test void trialRestoresArgumentsAndResetsInputCursor() throws Throwable {
		var item = BenchmarkDefinition.of(Matrix.class).cases().getFirst();
		var state = new ZlkJmhState();
		state.definitionClass = item.definitionClass(); state.sourceCase = item.sourceCase(); state.optionCase = item.optionCase();
		state.setup();
		var harness = new IntToIntJmhBenchmark();
		assertEquals(18, harness.run(state)); assertEquals(258, harness.run(state));
		state.setup(); assertEquals(18, harness.run(state));
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	public static class NullSource {
		@Source public static String source() { return null; }
		public static void verify(CompiledBenchmark c) {}
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	public static class ThrowingSource {
		@Source public static String source(@Values({"1"}) int value) { throw new UnsupportedOperationException("generator cause"); }
		public static void verify(CompiledBenchmark c, int value) {}
	}
	@Test void rejectsUnknownCasesAndPreservesGeneratorFailureContext() {
		var definition = BenchmarkDefinition.of(Matrix.class);
		for(String invalid : new String[] {"count=3;doubled=false", "doubled=false;count=1", "count=01;doubled=false", "count=1;doubled=False", "count=1;doubled=false;extra=1"})
			assertThrows(IllegalArgumentException.class, () -> definition.resolve(invalid, "stack=false;reuse=false"));
		assertThrows(IllegalArgumentException.class, () -> BenchmarkDefinition.of(NullSource.class).cases());
		var failed = assertThrows(IllegalArgumentException.class, () -> BenchmarkDefinition.of(ThrowingSource.class).cases());
		assertInstanceOf(UnsupportedOperationException.class, failed.getCause());
		assertTrue(failed.getMessage().contains("value=1"));
		assertTrue(failed.getMessage().contains("fixed"));
		assertTrue(failed.getMessage().contains(ThrowingSource.class.getName()));
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	public static class DuplicateSource extends ForkDefinitionFixture {
		@Source public static String other() { return ""; }
		@Source public static String third() { return ""; }
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	public static class InvalidBoolean {
		@Source public static String source(@Values({"TRUE"}) boolean value) { return ""; }
		public static void verify(CompiledBenchmark c, boolean value) {}
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	public static class DuplicateValues {
		@Source public static String source(@Values({"1", "1"}) int value) { return ""; }
		public static void verify(CompiledBenchmark c, int value) {}
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	public static class MissingVerify {
		@Source public static String source() { return ""; }
	}
	@Test void rejectsAmbiguousDefinitionsBeforeFork() {
		for(Class<?> invalid : new Class<?>[] {DuplicateSource.class, InvalidBoolean.class, DuplicateValues.class, MissingVerify.class})
			assertThrows(IllegalArgumentException.class, () -> BenchmarkDefinition.of(invalid), invalid.getName());
	}
	@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
	public static class Matrix {
		@Source public static String source(@Values({"1", "2"}) int count, @Values({"false", "true"}) boolean doubled) {
			return "module Bench\nbench x = add x " + (doubled ? count * 2 : count) + "\n";
		}
		@Options public static CompilationOptions options(@Values({"false", "true"}) boolean stack, @Values({"false", "true"}) boolean reuse) {
			return CompilationOptions.DEFAULT.set(CompilationOptions.Key.OPT_USE_OPERAND_STACK, stack).set(CompilationOptions.Key.OPT_REUSE_RECORD, reuse);
		}
		public static void verify(CompiledBenchmark compiled, int count, boolean doubled) throws Throwable {
			Integer actual = (Integer) compiled.handle().invokeExact(Integer.valueOf(17));
			assertEquals(17 + (doubled ? count * 2 : count), actual);
		}
	}
	@Test void expandsAndRestoresOrderedArgumentProducts() {
		var definition = BenchmarkDefinition.of(Matrix.class);
		var cases = definition.cases();
		assertEquals(16, cases.size());
		assertEquals("count=1;doubled=false", cases.getFirst().sourceCase());
		assertEquals("stack=false;reuse=false", cases.getFirst().optionCase());
		assertEquals("count=2;doubled=true", cases.getLast().sourceCase());
		for(var item : cases) {
			var restored = BenchmarkDefinition.of(Matrix.class).resolve(item.sourceCase(), item.optionCase());
			assertEquals(item.source(), restored.source());
			for(var key : CompilationOptions.Key.values()) assertEquals(item.options().isEnabled(key), restored.options().isEnabled(key));
		}
		assertSame(CompilationOptions.DEFAULT, BenchmarkDefinition.of(ForkDefinitionFixture.class).cases().getFirst().options());
	}
	@Test
	void testSourceDefinitionRunsInRealFork() throws Exception {
		Class<?> fixture = Class.forName("zlk.benchmark.ForkDefinitionFixture");
		Class<?> runner = Class.forName("zlk.benchmark.ZlkBenchmarkRunner");
		var output = java.nio.file.Path.of("target/benchmarks/fork-fixture", java.util.UUID.randomUUID().toString());
		var results = (java.util.Collection<?>) runner.getMethod("smoke", Class.class, java.nio.file.Path.class, String[].class)
				.invoke(null, fixture, output, new String[0]);
		assertEquals(1, results.size());
		var result = (org.openjdk.jmh.results.RunResult) results.iterator().next();
		assertEquals(1, result.getParams().getForks());
		assertEquals(fixture.getName(), result.getParams().getParam("definitionClass"));
		assertTrue(result.getSecondaryResults().containsKey("gc.alloc.rate.norm"));
		String metadata = java.nio.file.Files.readString(output.resolve("metadata.json"));
		assertTrue(metadata.contains("\"definitionClass\":\"" + fixture.getName() + "\""));
		assertTrue(metadata.contains("\"sourceSha256\""));
		assertTrue(metadata.contains("OPT_REUSE_RECORD"));
		assertTrue(metadata.contains("\"smoke\":true"));
		assertTrue(metadata.contains("\"forks\":1"));
		String markdown = java.nio.file.Files.readString(output.resolve("report.md"));
		assertTrue(markdown.contains("# JMH report"));
		assertTrue(markdown.contains("gc.alloc.rate.norm (B/op)"));
		for(var metric : java.util.List.of(result.getPrimaryResult(), result.getSecondaryResults().get("gc.alloc.rate.norm"))) {
			String score = java.math.BigDecimal.valueOf(metric.getScore()).toPlainString();
			assertTrue(markdown.contains("bar [" + score + "]"));
			assertTrue(markdown.contains("| fixed | " + score + " |"));
		}
	}
}
