package zlk.compiler.ir.idcalc;

import java.util.IdentityHashMap;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class ExpOrPatternMap<V> {
	private final IdentityHashMap<ExpOrPattern, V> impl;

	public ExpOrPatternMap() {
		this.impl = new IdentityHashMap<>();
	}

	public V get(ExpOrPattern exp) {
		V result = impl.get(exp);
		if(result == null) {
			throw new NoSuchElementException("exp: "+exp.buildString());
		}
		return result;
	}

	public void put(ExpOrPattern exp, V value) {
		V old = impl.put(exp, value);
		if(old != null) {
			throw new IllegalArgumentException(
					"already exist. exp: "+exp.buildString()+", old: "+old+", new: "+value);
		}
	}

	public void forEach(BiConsumer<? super ExpOrPattern, ? super V> action) {
		impl.forEach(action);
	}

	public <R> ExpOrPatternMap<R> traverse(Function<? super V, ? extends R> mapper) {
		ExpOrPatternMap<R> result = new ExpOrPatternMap<>();
		forEach((id, v) -> result.put(id, mapper.apply(v)));
		return result;
	}
}
