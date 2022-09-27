package zlk.common.id;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public class IdMap<V> implements PrettyPrintable, Cloneable {

	public Map<Id, V> impl;

	public IdMap() {
		impl = new HashMap<>();
	}

	public int size() {
		return impl.size();
	}

	public V getOrNull(Id id) {
		return impl.get(id);
	}

	public V get(Id id) {
		V result = getOrNull(id);
		if(result == null) {
			throw new NoSuchElementException("id: "+id);
		}
		return result;
	}

	public V getOrDefault(Id id, V value) {
		return impl.getOrDefault(id, value);
	}

	public void put(Id id, V value) {
		V old = impl.put(id, value);
		if(old != null) {
			throw new IllegalArgumentException("already exist. id: "+id+", old: "+old+", new: "+value);
		}
	}

	public void putOrConfirm(Id id, V value) {
		V old = impl.put(id, value);
		if(old != null && !old.equals(old)) {
			throw new IllegalArgumentException("not consistent. id: "+id+", old: "+old+", new: "+value);
		}
	}

	public boolean containsKey(Id id) {
		return impl.containsKey(id);
	}

	@Override
	public IdMap<V> clone() {
		IdMap<V> new_ = new IdMap<>();
		this.forEach(new_::put);
		return new_;
	}

	public void forEach(BiConsumer<? super Id, ? super V> action) {
		impl.forEach(action);
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
				return (l, r) -> { r.forEach(r::put); return l; };
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

	@Override
	public void mkString(PrettyPrinter pp) {
		boolean first = true;
		for(Map.Entry<Id, V> e : impl.entrySet()) {
			if(!first) {
				pp.append(", ");
			} else {
				pp.append("[ ");
				first = false;
			}
			pp.append(e.getKey()).append(":");
			if(e.getValue() instanceof PrettyPrintable p) {
				pp.append(p);
			} else {
				pp.append("--cannot print--");
			}
			pp.endl();
		}
		pp.append("]").endl();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		pp(sb);
		return sb.toString();
	}
}


