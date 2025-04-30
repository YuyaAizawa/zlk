package zlk.util.pp;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

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

	public static PrettyPrintable join(Iterator<? extends PrettyPrintable> iter, PrettyPrintable sep) {
		return pp -> {
			if(iter.hasNext()) {
				pp.append(iter.next());
				iter.forEachRemaining(e -> pp.append(sep).append(e));
			}
		};
	}

	public static PrettyPrintable join(Iterator<? extends PrettyPrintable> iter, CharSequence sep) {
		return pp -> {
			if(iter.hasNext()) {
				pp.append(iter.next());
				iter.forEachRemaining(e -> pp.append(sep).append(e));
			}
		};
	}

	public static <E> PrettyPrintable from(
			Collection<E> values,
			Function<? super E, PrettyPrintable> mapper
	) {
		return pp -> {
			switch(values.size()) {
			case 0:
				pp.append("[]");
				break;
			case 1:
				pp.append("[ ");
				pp.append(mapper.apply(values.iterator().next()));
				pp.append(" ]");
				break;
			default:
				pp.append("[").indent(() ->
					values.forEach(v -> {
						pp.endl();
						pp.append(mapper.apply(v)).append(",");
					})
				).endl().append("]");
			}
		};
	}

	public static <E> PrettyPrintable from(Collection<? extends PrettyPrintable> values) {
		return from(values, p -> p);
	}

	public static <K, V> PrettyPrintable from(
			Map<K, V> map,
			Function<? super K, PrettyPrintable> keyMapper,
			Function<? super V, PrettyPrintable> valueMapper
	) {
		return pp -> {
			switch(map.size()) {
			case 0:
				pp.append("{}");
				break;
			case 1:
				pp.append("{ ");
				appendEntry(pp, map.entrySet().iterator().next(), keyMapper, valueMapper);
				pp.append(" }");
				break;
			default:
				pp.append("{").indent(() ->
					map.entrySet().forEach(entry -> {
						pp.endl();
						appendEntry(pp, entry, keyMapper, valueMapper);
						pp.append(",");
					})
				).endl().append("}");
			}
		};
	}

	private static <K, V> void appendEntry(
			PrettyPrinter pp,
			Map.Entry<K, V> entry,
			Function<? super K, PrettyPrintable> keyMapper,
			Function<? super V, PrettyPrintable> valueMapper
	) {
		pp.append(keyMapper.apply(entry.getKey()));
		pp.append(": ");
		pp.append(valueMapper.apply(entry.getValue()));
	}
}
