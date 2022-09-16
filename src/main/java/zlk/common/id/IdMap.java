package zlk.common.id;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

@SuppressWarnings("serial")
public class IdMap<V> extends HashMap<Id, V>{

	public IdMap() {
		super();
	}

	public static <E, V> Collector<E, ?, IdMap<V>> collector(Function<? super E, ? extends Id> idExtractor, Function<? super E, ? extends V> valueExtractor) {
		return new Collector<E, IdMap<V>, IdMap<V>>() {

			@Override
			public Supplier<IdMap<V>> supplier() {
				return () -> new IdMap<>();
			}

			@Override
			public BiConsumer<IdMap<V>, E> accumulator() {
				return (map, e) -> map.put(idExtractor.apply(e), valueExtractor.apply(e));
			}

			@Override
			public BinaryOperator<IdMap<V>> combiner() {
				return (l, r) -> { r.entrySet().forEach(e -> r.put(e.getKey(), e.getValue())); return l; };
			}

			@Override
			public Function<IdMap<V>, IdMap<V>> finisher() {
				return Function.identity();
			}

			@Override
			public Set<Characteristics> characteristics() {
				return EnumSet.of(
						Collector.Characteristics.UNORDERED,
						Collector.Characteristics.IDENTITY_FINISH);
			}
		};
	}
}
