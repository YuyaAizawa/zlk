package zlk.phase.recon;

import java.util.IdentityHashMap;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.Function;

import zlk.ir.idcalc.IcExp;

public final class IcExpMap<V> {
	private final IdentityHashMap<IcExp, V> impl;

	public IcExpMap() {
		this.impl = new IdentityHashMap<>();
	}

	public V get(IcExp exp) {
		V result = impl.get(exp);
		if(result == null) {
			throw new NoSuchElementException("exp: "+exp.buildString());
		}
		return result;
	}

	public void put(IcExp exp, V value) {
		V old = impl.put(exp, value);
		if(old != null) {
			throw new IllegalArgumentException(
					"already exist. exp: "+exp.buildString()+", old: "+old+", new: "+value);
		}
	}

	public void forEach(BiConsumer<? super IcExp, ? super V> action) {
		impl.forEach(action);
	}

	public <R> IcExpMap<R> traverse(Function<? super V, ? extends R> mapper) {
		IcExpMap<R> result = new IcExpMap<>();
		forEach((id, v) -> result.put(id, mapper.apply(v)));
		return result;
	}
}
