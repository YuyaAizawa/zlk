package zlk.common.id;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public class IdMap<V> implements PrettyPrintable, Cloneable {

	public Map<Id, V> impl;

	private IdMap(Map<Id, V> impl) {
		this.impl = impl;
	}

	public IdMap() {
		this(new HashMap<>());
	}

	@SuppressWarnings("unchecked")
	public static <V> IdMap<V> of() {
		return (IdMap<V>) EMPTY;
	}
	private static final IdMap<?> EMPTY = new IdMap<>(Map.of());

	public static <V> IdMap<V> of(Id id, V value) {
		return new IdMap<>(Map.of(id, value));
	}

	public int size() {
		return impl.size();
	}

	public boolean isEmpty() {
		return size() == 0;
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

	public Optional<V> getOptional(Id id) {
		return Optional.ofNullable(getOrNull(id));
	}

	public V getOrDefault(Id id, V value) {
		return impl.getOrDefault(id, value);
	}

	public void processIfPresent(Id id, Consumer<? super V> action) {
		V v = getOrNull(id);
		if(v != null) {
			action.accept(v);
		}
	}

	public V computeIfAbsent(Id id, Function<? super Id, ? extends V> mappingFunction) {
		return impl.computeIfAbsent(id, mappingFunction);
	}

	public void put(Id id, V value) {
		V old = impl.put(Objects.requireNonNull(id), Objects.requireNonNull(value));
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

	public void remove(Id id) {
		impl.remove(id);
	}

	public Seq<Id> keys() {
		return Seq.from(impl.keySet());
	}

	public Seq<V> values() {
		return Seq.from(impl.values());
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

	public <R> IdMap<R> traverse(Function<? super V, ? extends R> mapper) {
		IdMap<R> result = new IdMap<>();
		forEach((id, v) -> result.put(id, mapper.apply(v)));
		return result;
	}

	public static <V> IdMap<V> union(IdMap<? extends V> primary, IdMap<? extends V> secondery) {
		IdMap<V> result = new IdMap<>();
		secondery.forEach(result::put);
		primary.forEach(result::put);
		return result;
	}

	public static <E, V> Seq.Folder<E, ?, IdMap<V>> folder(
			Function<? super E, ? extends Id> idExtractor,
			Function<? super E, ? extends V> valueExtractor
	) {
		return new Seq.Folder<E, IdMap<V>, IdMap<V>>() {

			@Override
			public BiFunction<? super E, IdMap<V>, IdMap<V>> accumulator() {
				return (e, idMap) -> { idMap.put(idExtractor.apply(e), valueExtractor.apply(e)); return idMap; };
			}

			@Override
			public IdMap<V> initialValue() {
				return new IdMap<>();
			}

			@Override
			public Function<? super IdMap<V>, ? extends IdMap<V>> finisher() {
				return Function.identity();
			}
		};
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(PrettyPrintable.tailComma(impl));
	}

	@Override
	public String toString() {
		return buildString();
	}
}

