package zlk.nameeval;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import zlk.common.id.Id;
import zlk.common.id.IdList;

public class Scc {
	private Scc() {}

	/**
	 * 強連結成分分解を行う
	 * @param graph 依存関係の有向グラフ
	 * @return 強連結成分のリスト
	 */
	public static List<IdList> decomp(IdToIds graph) {

		Set<Id> seen = new HashSet<>();
		graph.forEach((v, es) -> {
			seen.add(v);
			es.forEach(e -> seen.add(e));
		});
		IdList all = seen.stream().collect(IdList.collector());
		IdList postorder = new IdList();

		seen.clear();
		for(Id v : all) {
			if(!seen.contains(v)) {
				dfs(v, seen, graph, postorder);
			}
		}

		IdToIds rgraph = new IdToIds();
		graph.forEach((v, es) -> es.forEach(e -> rgraph.add(e, v)));

		seen.clear();
		List<IdList> result = new ArrayList<>();
		for(Id v : postorder.reversed()) {
			if(!seen.contains(v)) {
				IdList scc = new IdList();
				dfs(v, seen, rgraph, scc);
				result.add(scc);
			}
		}
		return result;
	}

	private static void dfs(Id v, Set<Id> seen, IdToIds graph, IdList acc) {
		seen.add(v);
		for(Id e : graph.get(v)) {
			if(!seen.contains(e)) {
				dfs(e, seen, graph, acc);
			}
		}
		// acc.add(v); TODO: 自己辺を反映した方が分かりやすい？
	}
}
