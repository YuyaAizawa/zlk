package zlk.benchmark;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;

/** 同じrunのJSONと共有する実測値の比較図．再測定やcase間の平均化は行わない． */
public final class JmhReport {
	private JmhReport() {}
	private record CaseKey(String source, String option) {}
	private record Point(Map<String, String> parameters, RunResult result) {}

	public static void write(Class<?> type, Path output, boolean smoke, Collection<RunResult> results) throws java.io.IOException {
		Files.writeString(output.resolve("report.md"), render(type, smoke, results), StandardCharsets.UTF_8);
		System.out.println("charts: " + output.resolve("report.md").toAbsolutePath());
	}
	public static String render(Class<?> type, boolean smoke, Collection<RunResult> results) {
		var definition = BenchmarkDefinition.of(type);
		Map<CaseKey, RunResult> byCase = new LinkedHashMap<>();
		for(var result : results) {
			var params = result.getParams();
			if(!params.getParam("definitionClass").equals(definition.name()))
				throw new IllegalArgumentException("unexpected report definition: " + params.getParam("definitionClass"));
			var key = new CaseKey(params.getParam("sourceCase"), params.getParam("optionCase"));
			if(byCase.putIfAbsent(key, result) != null) throw new IllegalArgumentException("duplicate report case: " + key);
		}
		Map<String, List<RunResult>> groups = new LinkedHashMap<>();
		List<Point> points = new ArrayList<>();
		for(var item : definition.cases()) {
			var result = byCase.remove(new CaseKey(item.sourceCase(), item.optionCase()));
			if(result == null) throw new IllegalArgumentException("missing report case: " + item.sourceCase() + " / " + item.optionCase());
			groups.computeIfAbsent(item.sourceCase(), key -> new ArrayList<>()).add(result);
			points.add(new Point(definition.parameters(item), result));
		}
		if(!byCase.isEmpty()) throw new IllegalArgumentException("unknown report cases: " + byCase.keySet());
		var params = results.iterator().next().getParams();
		StringBuilder out = new StringBuilder("# JMH report\n\n");
		out.append("定義：`").append(definition.name()).append("`\n\n")
			.append("smoke：").append(smoke).append("\n\n")
			.append("JVM args：`").append(String.join(" ", params.getJvmArgs())).append("`\n\n")
			.append("実行時間と割当量は小さいほど良い値です．1 opは生成関数1呼出しです．同一source条件のオプションを比較します．\n\n")
			.append("棒はJMHのscore，表はscoreと99.9%信頼区間の誤差です．未定義の誤差は「未定義」と表示します．\n\n");
		if(smoke) out.append("**smokeは動作確認用です．この数値から高速化や統計的な有意差を判断しないでください．**\n\n");
		if(!definition.configuration().gc()) out.append("GC profilerが無効のため，割当量は測定していません．\n\n");
		if(definition.jmhChart() != null) {
			renderGrouped(out, definition.jmhChart(), points, definition.configuration().gc());
		} else for(var group : groups.entrySet()) {
			out.append("## ").append(group.getKey()).append("\n\n");
			renderMetric(out, "実行時間", group.getValue(), RunResult::getPrimaryResult);
			if(definition.configuration().gc())
				renderMetric(out, "gc.alloc.rate.norm", group.getValue(), r -> r.getSecondaryResults().get("gc.alloc.rate.norm"));
		}
		return out.toString();
	}
	private static void renderGrouped(StringBuilder out, JmhChart chart, List<Point> points, boolean gc) {
		var parameters = points.getFirst().parameters().keySet();
		boolean paired = !chart.series().isEmpty();
		String compared = paired ? chart.series() : chart.x();
		if(!parameters.contains(chart.x()) || paired && (chart.x().equals(chart.series()) || !parameters.contains(chart.series())))
			throw new IllegalArgumentException("distinct known JMH chart parameters required: " + chart.x() + " / " + chart.series());
		var fixed = parameters.stream().filter(p -> !p.equals(chart.x()) && !p.equals(chart.series())).toList();
		Map<String, Map<Integer, Map<String, RunResult>>> groups = new LinkedHashMap<>();
		for(var point : points) {
			String condition = fixed.stream().map(p -> p + "=" + point.parameters().get(p)).collect(Collectors.joining("; "));
			int x = paired ? Integer.parseInt(point.parameters().get(chart.x())) : 0;
			String series = point.parameters().get(compared);
			if(!series.equals("false") && !series.equals("true")) throw new IllegalArgumentException("boolean JMH chart comparison required: " + compared);
			var values = groups.computeIfAbsent(condition, key -> new java.util.TreeMap<>()).computeIfAbsent(x, key -> new LinkedHashMap<>());
			if(values.putIfAbsent(series, point.result()) != null) throw new IllegalArgumentException("duplicate JMH chart point: " + point.parameters());
		}
		out.append(compared).append("=false：橙，").append(compared).append("=true：青．")
			.append(paired ? "各横軸値で2本の棒を並べます．\n\n" : "横軸でfalse／trueを比較します．\n\n")
			.append("横軸は等間隔のカテゴリです．各棒は実測値のみを表し，未測定の値を補完しません．\n\n");
		for(var group : groups.entrySet()) {
			List<RunResult> rows = new ArrayList<>();
			List<String> xs = new ArrayList<>();
			for(var x : group.getValue().entrySet()) {
				if(!x.getValue().keySet().equals(java.util.Set.of("false", "true"))) throw new IllegalArgumentException("missing JMH chart series at " + x.getKey());
				for(String series : List.of("false", "true")) {
					xs.add(paired ? Integer.toString(x.getKey()) : series);
					rows.add(x.getValue().get(series));
				}
			}
			out.append("## ").append(group.getKey().isEmpty() ? "all" : group.getKey()).append("\n\n");
			renderMetric(out, "実行時間", rows, RunResult::getPrimaryResult, chart, xs);
			if(gc) renderMetric(out, "gc.alloc.rate.norm", rows, r -> r.getSecondaryResults().get("gc.alloc.rate.norm"), chart, xs);
		}
	}
	private static void renderMetric(StringBuilder out, String metric, List<RunResult> rows, Function<RunResult, Result<?>> select) {
		renderMetric(out, metric, rows, select, null, rows.stream().map(r -> r.getParams().getParam("optionCase")).toList());
	}
	private static void renderMetric(StringBuilder out, String metric, List<RunResult> rows, Function<RunResult, Result<?>> select,
			JmhChart chart, List<String> xs) {
		boolean paired = chart != null && !chart.series().isEmpty();
		var values = rows.stream().map(select).toList();
		if(values.stream().anyMatch(v -> v == null)) throw new IllegalArgumentException("missing report metric: " + metric);
		String unit = values.getFirst().getScoreUnit();
		if(values.stream().anyMatch(v -> !v.getScoreUnit().equals(unit))) throw new IllegalArgumentException("inconsistent report units: " + metric);
		String title = metric + " (" + unit + ")";
		out.append("### ").append(title).append("\n\n");
		if(values.stream().allMatch(v -> Double.isFinite(v.getScore()) && v.getScore() >= 0)) {
			double max = values.stream().mapToDouble(Result::getScore).max().orElseThrow();
			List<String> labels = new ArrayList<>();
			for(int i = 0; i < xs.size(); i++) labels.add(paired ? xs.get(i) + " / " + (i % 2 == 0 ? "false" : "true") : xs.get(i));
			out.append("```mermaid\n");
			// 1系列の実測値だけを並べ，棒ごとの色をCSSで指定する．0の配置用slotはrendererによって可視となるため使わない．
			if(chart != null) out.append("---\nconfig:\n  themeVariables:\n    xyChart:\n      plotColorPalette: '#D55E00, #0072B2'\n")
				.append("  themeCSS: '.bar-plot-0 rect:nth-child(odd) { fill: #D55E00; stroke: #D55E00; } .bar-plot-0 rect:nth-child(even) { fill: #0072B2; stroke: #0072B2; }'\n---\n");
			out.append("xychart-beta\n")
				.append("    title ").append(quote(title)).append("\n")
				.append("    x-axis ").append(quote(chart == null ? "optionCase" : chart.x())).append(" [")
				.append(labels.stream().map(JmhReport::quote).collect(Collectors.joining(", "))).append("]\n")
				.append("    y-axis ").append(quote(unit)).append(" 0 --> ").append(number(Math.max(1, max))).append("\n");
			out.append("    bar [").append(values.stream().map(v -> number(v.getScore())).collect(Collectors.joining(", "))).append("]\n");
			out.append("```\n\n");
		} else {
			out.append("有限の非負scoreが揃わないため，図は出力しません．欠測を0で補完しません．\n\n");
		}
		if(chart == null) out.append("| optionCase");
		else out.append("| ").append(chart.x());
		if(paired) out.append(" | ").append(chart.series());
		out.append(" | score (").append(unit).append(") | 誤差 ± (99.9%) |\n")
			.append(paired ? "|---:|---|---:|---:|\n" : "|---|---:|---:|\n");
		for(int i = 0; i < rows.size(); i++) {
			var value = values.get(i);
			out.append("| ").append(xs.get(i));
			if(paired) out.append(" | ").append(i % 2 == 0 ? "false" : "true");
			out.append(" | ")
				.append(number(value.getScore())).append(" | ").append(number(value.getScoreError())).append(" |\n");
		}
		out.append('\n');
	}
	private static String number(double value) {
		return Double.isFinite(value) ? BigDecimal.valueOf(value).toPlainString() : "未定義";
	}
	private static String quote(String value) { return "\"" + value.replace("&", "#38;").replace("\"", "#34;") + "\""; }
}
