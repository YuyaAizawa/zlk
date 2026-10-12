package zlk.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 全定義caseの静的指標．JMHのmeasurementとは独立した入口． */
public final class BytecodeReport {
	private BytecodeReport() {}
	/** CSVと図で共有する，同一コンパイル結果の測定値． */
	public record Measurement(BenchmarkCase item, Map<String, String> parameters,
			int totalClassBytes, int moduleClassBytes, int classCount, BytecodeMetrics metrics) {
		public Measurement { parameters = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(parameters)); }
		public int value(ReportChart.Metric metric) {
			return switch(metric) {
				case TOTAL_CLASS_BYTES -> totalClassBytes;
				case MODULE_CLASS_BYTES -> moduleClassBytes;
				case CLASS_COUNT -> classCount;
				case CODE_LENGTH -> metrics.codeLength();
				case ALOAD -> metrics.aload();
				case ASTORE -> metrics.astore();
				case MAX_LOCALS -> metrics.maxLocals();
				case MAX_STACK -> metrics.maxStack();
				case UPDATE -> metrics.update();
				case INPLACE_UPDATE -> metrics.inplaceUpdate();
			};
		}
	}
	public static void main(String[] args) throws IOException, ClassNotFoundException {
		if(args.length < 3) throw new IllegalArgumentException("usage: BytecodeReport output.csv dump-directory|- definition-class...");
		java.util.ArrayList<Class<?>> definitions = new java.util.ArrayList<>();
		for(int i = 2; i < args.length; i++) definitions.add(Class.forName(args[i]));
		write(Path.of(args[0]), args[1].equals("-") ? null : Path.of(args[1]), definitions);
	}
	public static void write(Path output, Path dumps, List<Class<?>> definitions) throws IOException {
		output = output.toAbsolutePath();
		Files.createDirectories(output.getParent());
		int rows = 0;
		var measurements = new java.util.ArrayList<Measurement>();
		var chartDefinitions = new java.util.ArrayList<BenchmarkDefinition>();
		var keys = new java.util.HashSet<String>();
		try(var writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
			writer.write("definition_class,source_case,option_case,effective_options,source_sha256,module,method,descriptor,");
			writer.write(java.util.Arrays.stream(ReportChart.Metric.values()).map(ReportChart.Metric::column).collect(Collectors.joining(",")) + "\n");
			for(Class<?> type : definitions) {
				var definition = BenchmarkDefinition.of(type);
				chartDefinitions.add(definition);
				for(var item : definition.cases()) {
					if(!keys.add(item.definitionClass() + "\n" + item.sourceCase() + "\n" + item.optionCase()))
						throw new IllegalArgumentException("duplicate report case: " + item);
					var compiled = CompiledBenchmark.compile(item.source(), item.options());
					definition.verify(item, compiled);
					var metrics = BytecodeMetrics.read(compiled.classes().get("Bench"), "bench", compiled.descriptor());
					var measurement = new Measurement(item, definition.parameters(item),
							compiled.classes().values().stream().mapToInt(bytes -> bytes.length).sum(),
							compiled.classes().get("Bench").length, compiled.classes().size(), metrics);
					measurements.add(measurement);
					var cells = new java.util.ArrayList<>(List.of(item.definitionClass(), item.sourceCase(), item.optionCase(),
							BenchmarkMetadata.effectiveOptions(item.options()), BenchmarkMetadata.sha256(item.source()), "Bench", "bench", compiled.descriptor()));
					for(var metric : ReportChart.Metric.values()) cells.add(Integer.toString(measurement.value(metric)));
					writer.write(csv(cells));

					writer.write('\n');
					if(dumps != null) {
						Path dump = dumps.resolve("case-" + rows);
						Files.createDirectories(dump);
						Files.writeString(dump.resolve("Bench.zlk"), item.source(), StandardCharsets.UTF_8);
						Files.writeString(dump.resolve("case.json"), "{\"definitionClass\":" + BenchmarkMetadata.quote(item.definitionClass())
							+ ",\"sourceCase\":" + BenchmarkMetadata.quote(item.sourceCase()) + ",\"optionCase\":" + BenchmarkMetadata.quote(item.optionCase()) + "}\n");
						for(var entry : compiled.classes().entrySet()) {
							Path classFile = dump.resolve(entry.getKey().replace('.', '/') + ".class");
							Files.createDirectories(classFile.getParent()); Files.write(classFile, entry.getValue());
						}
					}
					rows++;
				}
			}
		}
		System.out.println(rows + " rows: " + output);
		Path markdown = output.resolveSibling("report.md");
		Files.writeString(markdown, MermaidReport.render(chartDefinitions, measurements), StandardCharsets.UTF_8);
		System.out.println("charts: " + markdown);

	}
	public static String csv(List<String> cells) {
		return cells.stream().map(cell -> cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0 || cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0
				? "\"" + cell.replace("\"", "\"\"") + "\"" : cell).collect(Collectors.joining(","));
	}
}
