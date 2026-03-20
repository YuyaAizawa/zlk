package zlk.repopt;

import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.function.BiConsumer;

import zlk.idcalc.ExpOrPattern;

public final class RepKeys {
	private final IdentityHashMap<ExpOrPattern, EnumSet<RepKey>> impl;

	public RepKeys() {
		this.impl = new IdentityHashMap<>();
	}

	public EnumSet<RepKey> get(ExpOrPattern node) {
		EnumSet<RepKey> keys = impl.get(node);
		if(keys == null) {
			return EnumSet.noneOf(RepKey.class);
		}
		return keys;
	}

	public void add(ExpOrPattern node, RepKey key) {
		EnumSet<RepKey> keys = impl.get(node);
		if(keys == null) {
			keys = EnumSet.of(key);
			impl.put(node, keys);
		} else {
			keys.add(key);
		}
	}

	public void remove(ExpOrPattern node, RepKey key) {
		EnumSet<RepKey> keys = impl.get(node);
		if(keys == null) {
			return;
		}
		keys.remove(key);
		if(keys.isEmpty()) {
			impl.remove(node);
		}
	}

	public void forEach(BiConsumer<? super ExpOrPattern, ? super EnumSet<RepKey>> action) {
		impl.forEach(action);
	}
}
