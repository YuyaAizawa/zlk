package zlk.benchmark;

import java.nio.file.Path;
import java.util.*;

/** test classesを含むclasspathから起動する共通CLI． */
public final class BenchmarkMain {
	private BenchmarkMain() {}
	public static void main(String[] args) throws Exception {
		if(args.length < 2 || !Set.of("run", "smoke", "report").contains(args[0]))
			throw new IllegalArgumentException("usage: BenchmarkMain run|smoke|report definition-class|all [output-root] [jvm-args...]");
		List<Class<?>> types = args[1].equals("all") ? BenchmarkDefinitions.classes()
				: List.of(Class.forName(args[1].contains(".") ? args[1] : "zlk.benchmark." + args[1]));
		Path root = Path.of(args.length > 2 ? args[2] : "target/benchmarks");
		if(args[0].equals("report")) {
			if(args.length > 3) throw new IllegalArgumentException("report accepts no JVM arguments");
			java.nio.file.Files.createDirectories(root);
			Path output = java.nio.file.Files.createDirectory(root.resolve(UUID.randomUUID().toString()));
			BytecodeReport.write(output.resolve("bytecode.csv"), output.resolve("classes"), types);
			return;
		}
		String[] jvmArgs = args.length > 3 ? Arrays.copyOfRange(args, 3, args.length) : new String[0];
		for(Class<?> type : types) {
			Path output = root.resolve(type.getSimpleName()).resolve(UUID.randomUUID().toString());
			if(args[0].equals("smoke")) ZlkBenchmarkRunner.smoke(type, output, jvmArgs);
			else ZlkBenchmarkRunner.run(type, output, jvmArgs);
		}
	}
}
