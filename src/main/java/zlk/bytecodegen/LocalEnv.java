package zlk.bytecodegen;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import zlk.common.Id;
import zlk.common.Type;

public class LocalEnv {
	Map<Id, LocalVar> impl = new HashMap<>();

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
