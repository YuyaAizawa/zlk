package zlk.benchmark;

import java.nio.file.*;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.*;
import org.openjdk.jmh.annotations.Mode;

public final class ZlkBenchmarkRunner {
	private ZlkBenchmarkRunner() {}
	public static Collection<RunResult> run(Class<?> definition, Path output, String... jvmArgs) throws Exception {
		Files.createDirectories(output);
		var results = new Runner(options(definition, output, false, jvmArgs)).run();
		BenchmarkMetadata.write(definition, output, false, results);
		return results;
	}
	public static Collection<RunResult> smoke(Class<?> definition, Path output, String... jvmArgs) throws Exception {
		Files.createDirectories(output);
		var results = new Runner(options(definition, output, true, jvmArgs)).run();
		BenchmarkMetadata.write(definition, output, true, results);
		return results;
	}
	public static org.openjdk.jmh.runner.options.Options options(Class<?> type, Path output, boolean smoke, String... jvmArgs) {
		var definition = BenchmarkDefinition.of(type);
		var cases = definition.cases();
		var config = definition.configuration();
		String harness = switch(config.shape()) {
			case INT_TO_INT -> IntToIntJmhBenchmark.class.getName();
			case INT_TO_RECORD -> IntToRecordJmhBenchmark.class.getName();
		};
		var builder = new OptionsBuilder().include("^" + java.util.regex.Pattern.quote(harness + ".run") + "$")
				.param("definitionClass", type.getName())
				.param("sourceCase", cases.stream().map(BenchmarkCase::sourceCase).distinct().toArray(String[]::new))
				.param("optionCase", cases.stream().map(BenchmarkCase::optionCase).distinct().toArray(String[]::new))
				.forks(smoke ? 1 : config.forks()).warmupIterations(smoke ? 1 : config.warmupIterations())
				.measurementIterations(smoke ? 1 : config.measurementIterations())
				.warmupTime(smoke ? TimeValue.milliseconds(100) : TimeValue.seconds(config.seconds()))
				.measurementTime(smoke ? TimeValue.milliseconds(100) : TimeValue.seconds(config.seconds()))
				.threads(1).mode(Mode.AverageTime).timeUnit(TimeUnit.NANOSECONDS).shouldFailOnError(true)
				.jvmArgsAppend(jvmArgs)
				.resultFormat(ResultFormatType.JSON).result(output.resolve("results.json").toString());
		if(config.gc()) builder.addProfiler(GCProfiler.class);
		return builder.build();
	}
}
