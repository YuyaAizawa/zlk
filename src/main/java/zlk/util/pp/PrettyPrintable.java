package zlk.util.pp;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface PrettyPrintable {
	void mkString(PrettyPrinter pp);

	default void mkStringWithoutLineBreak(PrettyPrinter pp) {
		mkString(pp.wrap(Wrapper.spaceEndl()));
	}

	default void pp(Appendable out) {
		mkString(new BasicPrettyPrinter(out));
	}

	default String buildString() {
		StringBuilder sb = new StringBuilder();
		mkString(new BasicPrettyPrinter(sb));
		return sb.toString();
	}

	private static PrettyPrintable join(Iterator<? extends PrettyPrintable> iter, PrettyPrintable sep) {
		return pp -> {
			if(iter.hasNext()) {
				pp.append(iter.next());
				iter.forEachRemaining(e -> pp.append(sep).append(e));
			}
		};
	}

	public static PrettyPrintable join(Iterable<? extends PrettyPrintable> iterable, PrettyPrintable sep) {
		return join(iterable.iterator(), sep);
	}

	public static PrettyPrintable join(Iterable<? extends PrettyPrintable> iterable, CharSequence sep) {
		return join(iterable, pp -> pp.append(sep));
	}

	// TODO: tailCommaとoneLineはprinter側のNoBreakで切り替えるように
	public static PrettyPrintable oneLine(List<? extends PrettyPrintable> list) {
		if(list.isEmpty()) {
			return pp -> pp.append("[]");
		}


		return pp -> {
			pp.withoutLineBreak(pp_ -> {
				pp_.append("[").append(join(list, ", ")).append("]");
			});
		};
	}

	public static PrettyPrintable tailComma(List<? extends PrettyPrintable> list) {
		if(list.isEmpty()) {
			return pp -> pp.append("[]");
		}

		return pp -> {
			pp.append("[").endl();
			pp.indent(() -> {
				pp.append(join(list, pp_ -> pp_.append(",").endl()));
				pp.append(",").endl();
			});
			pp.append("]");
		};
	}

	public static PrettyPrintable oneLine(Map<?, ?> map) {
		if(map.isEmpty()) {
			return pp -> pp.append("{}");
		}

		Iterator<PrettyPrintable> iter = map.keySet()
				.stream()
				.sorted()
				.map(k -> (PrettyPrintable)(pp_ -> appendEntry(k, map.get(k), pp_)))
				.iterator();

		return pp -> {
			pp.withoutLineBreak(pp_ -> {
				pp_.append("{").append(join(iter, pp__ -> pp__.append(", "))).append("}");
			});
		};
	}

	public static PrettyPrintable tailComma(Map<?, ?> map) {
		if(map.isEmpty()) {
			return pp -> pp.append("{}");
		}

		Iterator<PrettyPrintable> iter = map.keySet()
				.stream()
				.sorted()
				.map(k -> (PrettyPrintable)(pp_ -> appendEntry(k, map.get(k), pp_)))
				.iterator();

		return pp -> {
			pp.append("{").endl();
			pp.indent(() -> {
				pp.append(join(iter, pp_ -> pp_.append(",").endl()));
				pp.append(",").endl();
			});
			pp.append("}");
		};
	}

	private static void appendEntry(Object k, Object v, PrettyPrinter pp) {
		if(k instanceof PrettyPrintable kp) {
			pp.append(kp);
		} else {
			pp.append(k.toString());
		}
		pp.append(": ");
		if(v instanceof PrettyPrintable vp) {
			pp.append(vp);
		} else {
			pp.append(v.toString());
		}
	}
}