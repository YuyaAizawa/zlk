package zlk.nameeval;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import zlk.common.type.Type;

public final class TyEnv {
	private final Map<String, Type> impl;

	public TyEnv() {
		impl = new HashMap<>();
	}

	public void put(String name, Type ty) {
		Type old = impl.put(name, ty);
		if(old != null) {
			throw new RuntimeException("already exist: "+old);
		}
	}

	public Type get(String name) {
		Type result = impl.get(name);
		if(result == null) {
			throw new NoSuchElementException(name);
		}
		return result;
	}
}
