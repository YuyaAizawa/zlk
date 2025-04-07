package zlk.bytecodegen;

import static zlk.util.ErrorUtils.todo;

import java.util.NoSuchElementException;

import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;

public class LocalEnv {
	IdMap<LocalVar> impl = new IdMap<>();

	public LocalVar bind(Id idInfo, Type type) {
		int idx = impl.size();
		LocalVar localVar = new LocalVar(idx, type);

		return switch(type) {
		case Type.Atom _ -> {
			impl.put(idInfo, localVar);
			yield localVar;
		}
		default ->
			todo();
		};
	}

	public LocalVar find(Id idInfo) {
		LocalVar result = impl.get(idInfo);
		if(result == null) {
			throw new NoSuchElementException(idInfo.toString());
		}
		return result;
	}
}
