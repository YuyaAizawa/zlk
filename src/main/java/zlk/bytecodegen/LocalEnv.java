package zlk.bytecodegen;

import java.util.NoSuchElementException;

import zlk.common.Id;
import zlk.common.IdMap;
import zlk.common.Type;

public class LocalEnv {
	IdMap<LocalVar> impl = new IdMap<>();

	public LocalVar bind(Id idInfo) {
		int idx = impl.size();
		Type type = idInfo.type();
		LocalVar localVar = new LocalVar(idx, type);

		if(type == Type.i32) {
			impl.put(idInfo, localVar);
		} else {
			throw new IllegalArgumentException(idInfo.toString());
		}
		return localVar;
	}

	public LocalVar find(Id idInfo) {
		LocalVar result = impl.get(idInfo);
		if(result == null) {
			throw new NoSuchElementException(idInfo.toString());
		}
		return result;
	}
}
