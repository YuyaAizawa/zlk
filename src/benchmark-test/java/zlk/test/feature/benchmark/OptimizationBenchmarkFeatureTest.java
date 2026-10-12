package zlk.test.feature.benchmark;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import zlk.benchmark.CompiledBenchmark;
import zlk.benchmark.BenchmarkSources;
import zlk.runtime.ZlkRecord;

public class OptimizationBenchmarkFeatureTest {
	@Test
	void reportContainsEveryUniqueCaseAndReproducibleSourceHash() throws Exception {
		assertEquals("\"a,b\",\"a\"\"b\",\"a\nb\",", zlk.benchmark.BytecodeReport.csv(java.util.List.of("a,b", "a\"b", "a\nb", "")));
		var file = java.nio.file.Path.of("target/benchmarks/test-report/bytecode.csv");
		var dumps = file.getParent().resolve("classes");
		zlk.benchmark.BytecodeReport.write(file, dumps, zlk.benchmark.BenchmarkDefinitions.classes());
		var lines = java.nio.file.Files.readAllLines(file);
		var cases = zlk.benchmark.BenchmarkDefinitions.classes().stream().flatMap(t -> zlk.benchmark.BenchmarkDefinition.of(t).cases().stream()).toList();
		assertEquals(cases.size() + 1, lines.size());
		assertTrue(lines.getFirst().startsWith("definition_class,source_case,option_case,effective_options,source_sha256,"));
		var keys = new java.util.HashSet<String>();
		for(int i = 1; i < lines.size(); i++) {
			String[] cells = lines.get(i).split(",", -1);
			assertEquals(18, cells.length);
			assertTrue(keys.add(String.join(",", java.util.Arrays.copyOf(cells, 3))));
			var item = cases.get(i - 1);
			assertEquals(item.definitionClass(), cells[0]); assertEquals(item.sourceCase(), cells[1]); assertEquals(item.optionCase(), cells[2]);
			assertEquals(zlk.benchmark.BenchmarkMetadata.effectiveOptions(item.options()), cells[3]);
			String savedSource = java.nio.file.Files.readString(dumps.resolve("case-" + (i - 1)).resolve("Bench.zlk"));
			assertEquals(item.source(), savedSource);
			assertEquals(zlk.benchmark.BenchmarkMetadata.sha256(savedSource), cells[4]);
			var compiled = CompiledBenchmark.compile(item.source(), item.options());
			assertEquals(compiled.descriptor(), cells[7]);
			assertEquals(compiled.classes().values().stream().mapToInt(b -> b.length).sum(), Integer.parseInt(cells[8]));
			assertEquals(compiled.classes().get("Bench").length, Integer.parseInt(cells[9]));
			assertEquals(compiled.classes().size(), Integer.parseInt(cells[10]));
			byte[] savedClass = java.nio.file.Files.readAllBytes(dumps.resolve("case-" + (i - 1)).resolve("Bench.class"));
			assertArrayEquals(compiled.classes().get("Bench"), savedClass);
			var metrics = zlk.benchmark.BytecodeMetrics.read(savedClass, "bench", cells[7]);
			assertEquals(metrics.codeLength(), Integer.parseInt(cells[11]));
		}
	}
	@Test
	void metricsMatchGeneratedMethodAndReadBytesRatherThanCallbacks() {
		for(var item : cases()) {
			var compiled = CompiledBenchmark.compile(item.source(), item.options());
			var metrics = zlk.benchmark.BytecodeMetrics.read(compiled.classes().get("Bench"), "bench", compiled.descriptor());
			int[] expected = observe(compiled);
			assertEquals(expected[0], metrics.aload()); assertEquals(expected[1], metrics.astore());
			assertEquals(expected[2], metrics.update()); assertEquals(expected[3], metrics.inplaceUpdate());
			assertEquals(expected[4], metrics.maxStack()); assertEquals(expected[5], metrics.maxLocals());
			assertTrue(metrics.codeLength() > metrics.aload() + metrics.astore());
			assertThrows(IllegalArgumentException.class, () -> zlk.benchmark.BytecodeMetrics.read(compiled.classes().get("Bench"), "bench", "()V"));
		}
		var cw = new org.objectweb.asm.ClassWriter(0);
		cw.visit(org.objectweb.asm.Opcodes.V23, org.objectweb.asm.Opcodes.ACC_PUBLIC, "Wide", null, "java/lang/Object", null);
		cw.newConst(1234567890123L); cw.newConst(1.25d);
		var mv = cw.visitMethod(org.objectweb.asm.Opcodes.ACC_PUBLIC | org.objectweb.asm.Opcodes.ACC_STATIC, "bench", "()Ljava/lang/Object;", null, null);
		mv.visitCode(); mv.visitInsn(org.objectweb.asm.Opcodes.ACONST_NULL);
		mv.visitVarInsn(org.objectweb.asm.Opcodes.ASTORE, 300); mv.visitVarInsn(org.objectweb.asm.Opcodes.ALOAD, 300);
		mv.visitInsn(org.objectweb.asm.Opcodes.ARETURN); mv.visitMaxs(1, 301); mv.visitEnd(); cw.visitEnd();
		var wide = zlk.benchmark.BytecodeMetrics.read(cw.toByteArray(), "bench", "()Ljava/lang/Object;");
		assertEquals(10, wide.codeLength()); assertEquals(1, wide.aload()); assertEquals(1, wide.astore());
		assertEquals(301, wide.maxLocals()); assertEquals(1, wide.maxStack());
		assertThrows(IllegalArgumentException.class, () -> zlk.benchmark.BytecodeMetrics.read(new byte[] {0}, "bench", "()V"));
		byte[] invalidMagic = cw.toByteArray(); invalidMagic[0] = 0;
		assertThrows(IllegalArgumentException.class, () -> zlk.benchmark.BytecodeMetrics.read(invalidMagic, "bench", "()Ljava/lang/Object;"));
	}
	@Test
	void subjectsTriggerOnlyTheirIntendedOptimizations() {
		var all = cases();
		for(var item : all) {
			var sample = sample(item);
			int[] counts = observe(CompiledBenchmark.compile(item.source(), item.options()));
			boolean reuse = item.options().isEnabled(zlk.compiler.CompilationOptions.Key.OPT_REUSE_RECORD)
					&& sample.subject() != BenchmarkSources.Subject.RECORD_SHARED;
			int calls = sample.updates() * sample.updatedFields();
			assertEquals(reuse ? 0 : calls, counts[2], item.toString());
			assertEquals(reuse ? calls : 0, counts[3], item.toString());
			if(sample.subject() != BenchmarkSources.Subject.OPERAND_STACK
					&& sample.subject() != BenchmarkSources.Subject.NAMED_LOCAL
					&& sample.subject() != BenchmarkSources.Subject.COMBINED) continue;
			if(item.options().isEnabled(zlk.compiler.CompilationOptions.Key.OPT_USE_OPERAND_STACK)) continue;
			var on = all.stream().filter(c -> c.definitionClass().equals(item.definitionClass()) && c.sourceCase().equals(item.sourceCase())
					&& c.options().isEnabled(zlk.compiler.CompilationOptions.Key.OPT_USE_OPERAND_STACK)
					&& c.options().isEnabled(zlk.compiler.CompilationOptions.Key.OPT_REUSE_RECORD) == item.options().isEnabled(zlk.compiler.CompilationOptions.Key.OPT_REUSE_RECORD))
					.findFirst().orElseThrow();
			int[] optimized = observe(CompiledBenchmark.compile(on.source(), on.options()));
			if(sample.subject() == BenchmarkSources.Subject.NAMED_LOCAL) {
				assertEquals(counts[0], optimized[0]); assertEquals(counts[1], optimized[1]);
			} else {
				assertTrue(counts[0] > optimized[0], item.toString()); assertTrue(counts[1] > optimized[1], item.toString());
			}
		}
	}

	private static java.util.List<zlk.benchmark.BenchmarkCase> cases() {
		return zlk.benchmark.BenchmarkDefinitions.classes().stream().flatMap(t -> zlk.benchmark.BenchmarkDefinition.of(t).cases().stream()).toList();
	}
	private static BenchmarkSources.Sample sample(zlk.benchmark.BenchmarkCase item) {
		var subject = java.util.Arrays.stream(BenchmarkSources.Subject.values()).filter(s -> item.definitionClass().endsWith("." + s.benchmarkName())).findFirst().orElseThrow();
		var params = new java.util.HashMap<String, Integer>();
		if(!item.sourceCase().equals("fixed")) for(String part : item.sourceCase().split(";")) {
			String[] pair = part.split("="); params.put(pair[0], Integer.valueOf(pair[1]));
		}
		return new BenchmarkSources.Sample(subject, params.getOrDefault("fields", 0),
				params.getOrDefault("updates", subject == BenchmarkSources.Subject.RECORD_SHARED ? 1 : 0), params.getOrDefault("depth", 0));
	}

	private static int[] observe(CompiledBenchmark compiled) {
		int[] counts = new int[6];
		new org.objectweb.asm.ClassReader(compiled.classes().get("Bench")).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
			@Override
			public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				if(!name.equals("bench") || !descriptor.equals(compiled.descriptor())) return null;
				return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
					@Override public void visitVarInsn(int opcode, int slot) {
						if(opcode == org.objectweb.asm.Opcodes.ALOAD) counts[0]++;
						if(opcode == org.objectweb.asm.Opcodes.ASTORE) counts[1]++;
					}
					@Override public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
						if(opcode == org.objectweb.asm.Opcodes.INVOKEINTERFACE && itf && owner.equals("zlk/runtime/ZlkRecord")
								&& name.equals("update") && desc.equals("(Ljava/lang/String;Ljava/lang/Object;)Lzlk/runtime/ZlkRecord;")) counts[2]++;
						if(opcode == org.objectweb.asm.Opcodes.INVOKESTATIC && !itf && owner.equals("zlk/runtime/internal/RecordOps")
								&& name.equals("inplaceUpdate") && desc.equals("(Lzlk/runtime/ZlkRecord;Ljava/lang/String;Ljava/lang/Object;)Lzlk/runtime/ZlkRecord;")) counts[3]++;
					}
					@Override public void visitMaxs(int stack, int locals) { counts[4] = stack; counts[5] = locals; }
				};
			}
		}, org.objectweb.asm.ClassReader.SKIP_DEBUG | org.objectweb.asm.ClassReader.SKIP_FRAMES);
		return counts;
	}
	@Test
	void jmhTrialSetupLinksAndChecksEverySubject() throws Throwable {
		for(var subject : BenchmarkSources.Subject.values()) {
			var definition = zlk.benchmark.BenchmarkDefinition.of(Class.forName("zlk.benchmark." + subject.benchmarkName()));
			for(var item : definition.cases()) {
				var state = new zlk.benchmark.ZlkJmhState();
				state.definitionClass = item.definitionClass(); state.sourceCase = item.sourceCase(); state.optionCase = item.optionCase();
				state.setup();
			}
		}
	}
	@Test
	void allSubjectsHaveIndependentExpectedSemantics() throws Throwable {
		for(var item : cases()) {
			var sample = sample(item);
			var compiled = CompiledBenchmark.compile(item.source(), item.options());
			for(Integer input : new Integer[] {17, -31, 1009, Integer.MAX_VALUE}) {
				if(sample.fields() == 0) {
					Integer actual = (Integer) compiled.handle().invokeExact(input);
					int expected = input;
					if(sample.subject() == BenchmarkSources.Subject.NAMED_LOCAL) {
						int sum = input + input;
						expected = sum * sum;
					} else {
						for(int i = 0; i < sample.depth(); i++) expected += input * input;
					}
					assertEquals(expected, actual, sample.toString());
				} else {
					ZlkRecord actual = (ZlkRecord) compiled.handle().invokeExact(input);
					if(sample.subject() == BenchmarkSources.Subject.RECORD_SHARED) {
						ZlkRecord original = (ZlkRecord) actual.get("original");
						for(int f = 0; f < sample.fields(); f++) assertEquals(input + f + 1, original.get("f" + f));
						actual = (ZlkRecord) actual.get("changed");
					}
					int[] expected = new int[sample.fields()];
					for(int f = 0; f < expected.length; f++) expected[f] = input + f + 1;
					for(int u = 0; u < sample.updates(); u++) {
						for(int j = 0; j < sample.updatedFields(); j++) {
							int f = (u * sample.updatedFields() + j) % sample.fields();
							expected[f] = sample.subject() == BenchmarkSources.Subject.COMBINED
								? input * input + input + u + f + 1 : input + u + 1;
						}
					}
					for(int f = 0; f < expected.length; f++) assertEquals(expected[f], actual.get("f" + f), sample.toString());
				}
			}
		}
	}
	@Test
	void invokesDynamicArithmeticThroughTypedHandle() throws Throwable {
		var compiled = CompiledBenchmark.compile(
				"module Bench\nbench x = add (mul x x) x\n",
				zlk.compiler.CompilationOptions.DEFAULT);
		Integer result = (Integer) compiled.handle().invokeExact(Integer.valueOf(17));
		assertEquals(306, result);
		assertEquals("(Ljava/lang/Integer;)Ljava/lang/Integer;", compiled.descriptor());
	}
}
