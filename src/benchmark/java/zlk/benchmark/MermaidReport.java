package zlk.benchmark;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import zlk.benchmark.BytecodeReport.Measurement;

/** 静的測定値の投影とMermaid出力．コンパイルや平均化は行わない． */
public final class MermaidReport {
	private MermaidReport() {}
	public static String render(List<BenchmarkDefinition> definitions, List<Measurement> measurements) {
		StringBuilder out = new StringBuilder("# Bytecode report\n\n");
		out.append("静的なbytecode指標です．実行時間や割当量は含みません．\n\n")
			.append("横軸はカテゴリとして等間隔に表示します．表の系列番号は図中の宣言順です．同じ値の線は重なります．\n\n");
		for(var definition : definitions) {
			out.append("## ").append(definition.name()).append("\n\n");
			var rows = measurements.stream().filter(m -> m.item().definitionClass().equals(definition.name())).toList();
			if(definition.charts().isEmpty()) {
				out.append("`@ReportChart` が未指定です．測定値はCSVを参照してください．\n\n");
				continue;
			}
			for(var chart : definition.charts()) renderChart(out, definition.name(), chart, rows);
		}
		return out.toString();
	}
	private static void renderChart(StringBuilder out, String definition, ReportChart chart, List<Measurement> rows) {
		if(rows.isEmpty()) throw new IllegalArgumentException("no measurements: " + definition);
		var parameters = rows.getFirst().parameters().keySet();
		Set<String> used = new LinkedHashSet<>();
		used.add(chart.x());
		for(String parameter : chart.series()) {
			if(!used.add(parameter)) throw new IllegalArgumentException("duplicate x/series parameter: " + parameter + " in " + definition);
		}
		if(!parameters.containsAll(used)) throw new IllegalArgumentException("unknown chart parameter: " + used + " in " + definition);
		if(chart.metrics().length == 0 || new HashSet<>(List.of(chart.metrics())).size() != chart.metrics().length)
			throw new IllegalArgumentException("nonempty distinct chart metrics required: " + definition);
		var groupBy = parameters.stream().filter(p -> !used.contains(p)).toList();
		Map<List<String>, List<Measurement>> groups = new LinkedHashMap<>();
		for(var row : rows) {
			if(!row.parameters().keySet().equals(parameters)) throw new IllegalArgumentException("inconsistent chart parameters: " + definition);
			groups.computeIfAbsent(values(row, groupBy), key -> new ArrayList<>()).add(row);
		}
		for(var group : groups.entrySet()) {
			var xValues = group.getValue().stream().map(r -> r.parameters().get(chart.x())).distinct()
					.sorted(Comparator.comparingInt(MermaidReport::order)).toList();
			Map<List<String>, Map<String, Measurement>> series = new LinkedHashMap<>();
			for(var row : group.getValue()) {
				var points = series.computeIfAbsent(values(row, List.of(chart.series())), key -> new LinkedHashMap<>());
				if(points.putIfAbsent(row.parameters().get(chart.x()), row) != null)
					throw new IllegalArgumentException("duplicate chart point: " + definition + " " + row.item());
			}
			for(var points : series.values()) {
				if(!points.keySet().equals(new HashSet<>(xValues))) throw new IllegalArgumentException("missing chart point: " + definition);
			}
			if(chart.plot() == ReportChart.Plot.BAR && series.size() > 1)
				throw new IllegalArgumentException("bar chart requires one series (use compared parameter as x): " + definition);
			String condition = label(groupBy, group.getKey());
			for(var metric : chart.metrics()) {
				String title = metric.column() + (condition.isEmpty() ? "" : " / " + condition);
				out.append("### ").append(title).append("\n\n```mermaid\nxychart-beta\n")
					.append("    title ").append(quote(title)).append("\n")
					.append("    x-axis ").append(quote(chart.x())).append(" [")
					.append(xValues.stream().map(MermaidReport::quote).collect(Collectors.joining(", "))).append("]\n")
					.append("    y-axis ").append(quote(metric.column())).append(" 0 --> ")
					.append(Math.max(1, group.getValue().stream().mapToInt(r -> r.value(metric)).max().orElseThrow())).append("\n");
				for(var entry : series.entrySet()) {
					String name = label(List.of(chart.series()), entry.getKey());
					out.append("    ").append(chart.plot() == ReportChart.Plot.LINE ? "line" : "bar");
					if(chart.namedSeries()) out.append(' ').append(quote(name.isEmpty() ? "all" : name));
					out.append(" [").append(xValues.stream().map(x -> Integer.toString(entry.getValue().get(x).value(metric)))
							.collect(Collectors.joining(", "))).append("]\n");
				}
				out.append("```\n\n| 系列 | 条件");
				for(String x : xValues) out.append(" | ").append(x);
				out.append(" |\n|---:|---");
				for(String x : xValues) out.append("|---:");
				out.append("|\n");
				int index = 1;
				for(var entry : series.entrySet()) {
					String name = label(List.of(chart.series()), entry.getKey());
					out.append("| ").append(index++).append(" | ").append(name.isEmpty() ? "all" : name);
					for(String x : xValues) out.append(" | ").append(entry.getValue().get(x).value(metric));
					out.append(" |\n");
				}
				out.append('\n');
			}
		}
	}
	private static List<String> values(Measurement row, List<String> parameters) {
		return parameters.stream().map(row.parameters()::get).toList();
	}
	private static String label(List<String> parameters, List<String> values) {
		List<String> parts = new ArrayList<>();
		for(int i = 0; i < parameters.size(); i++) parts.add(parameters.get(i) + "=" + values.get(i));
		return String.join("; ", parts);
	}
	private static int order(String value) {
		return switch(value) { case "false" -> 0; case "true" -> 1; default -> Integer.parseInt(value); };
	}
	/** Mermaidの引用内ではJSONのbackslash escapeではなくentityを用いる． */
	private static String quote(String value) { return "\"" + value.replace("&", "#38;").replace("\"", "#34;") + "\""; }
}
