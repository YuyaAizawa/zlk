package zlk.phase.nameeval;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import zlk.common.id.Id;

/** 型名からIdへの対応だけを保持する名前環境． */
final class TypeEnv {
	private final Map<String, Id> impl = new HashMap<>();

	Id register(String name, Id id) throws DuplicatedNameException {
		Id old = impl.putIfAbsent(name, id);
		if (old != null) {
			throw new DuplicatedNameException(old, id);
		}
		return id;
	}

	Id get(String name) {
		Id id = impl.get(name);
		if (id == null) {
			throw new NoSuchElementException(name);
		}
		return id;
	}

	Id getOrNull(String name) {
		return impl.get(name);
	}
}
