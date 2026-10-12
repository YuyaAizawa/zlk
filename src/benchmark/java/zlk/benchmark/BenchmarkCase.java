package zlk.benchmark;

import zlk.compiler.CompilationOptions;

/** forkで復元可能な識別子と，その入力から生成した不変な測定条件． */
public record BenchmarkCase(String definitionClass, String sourceCase, String optionCase,
		String source, CompilationOptions options) {}
