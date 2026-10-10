package zlk.benchmark;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.stream.Collectors;
import zlk.compiler.CompilationOptions;
import org.openjdk.jmh.results.RunResult;

/** JSONとCSVで共有する実効条件とUTF-8 source hash． */
public final class BenchmarkMetadata {
	private BenchmarkMetadata() {}
	public static String sha256(String source) {
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))); }
		catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
	}
	public static String effectiveOptions(CompilationOptions options) {
		return Arrays.stream(CompilationOptions.Key.values()).map(k -> k + "=" + options.isEnabled(k)).collect(Collectors.joining(";"));
	}
	public static String quote(String text) {
		StringBuilder out = new StringBuilder("\"");
		for(char c : text.toCharArray()) {
			switch(c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> { if(c < 32) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
			}
		}
		return out.append('"').toString();
	}
	public static void write(Class<?> type, Path output, boolean smoke, Collection<RunResult> results) throws java.io.IOException {
		var definition = BenchmarkDefinition.of(type);
		var params = results.iterator().next().getParams();
		String cases = definition.cases().stream().map(c -> "{\"definitionClass\":" + quote(c.definitionClass())
				+ ",\"sourceCase\":" + quote(c.sourceCase()) + ",\"optionCase\":" + quote(c.optionCase())
				+ ",\"sourceSha256\":" + quote(sha256(c.source())) + ",\"effectiveOptions\":" + quote(effectiveOptions(c.options())) + "}")
				.collect(Collectors.joining(","));
		String args = params.getJvmArgs().stream().map(BenchmarkMetadata::quote).collect(Collectors.joining(","));
		Files.writeString(output.resolve("metadata.json"), "{\"definitionClass\":" + quote(type.getName())
				+ ",\"shape\":" + quote(definition.configuration().shape().name()) + ",\"smoke\":" + smoke
				+ ",\"forks\":" + params.getForks() + ",\"threads\":" + params.getThreads()
				+ ",\"warmupIterations\":" + params.getWarmup().getCount() + ",\"warmupTime\":" + quote(params.getWarmup().getTime().toString())
				+ ",\"measurementIterations\":" + params.getMeasurement().getCount() + ",\"measurementTime\":" + quote(params.getMeasurement().getTime().toString())
				+ ",\"mode\":" + quote(params.getMode().name()) + ",\"timeUnit\":" + quote(params.getTimeUnit().name())
				+ ",\"gc\":" + definition.configuration().gc() + ",\"jvmArgs\":[" + args + "]"
				+ ",\"jdkVersion\":" + quote(params.getJdkVersion()) + ",\"vmName\":" + quote(params.getVmName())
				+ ",\"jmhVersion\":" + quote(params.getJmhVersion()) + ",\"cases\":[" + cases + "]}\n", StandardCharsets.UTF_8);
	}
}
