package zlk.bytecodegen;

import java.util.ArrayList;
import java.util.NoSuchElementException;

import zlk.common.Type;
import zlk.idcalc.IdInfo;

public class LocalEnv {
	ArrayList<Kvp> impl = new ArrayList<>();

	public LocalVar bind(IdInfo idInfo) {
		int idx = impl.size();
		Type type = idInfo.type();
		LocalVar localVar = new LocalVar(idx, type);

		Kvp kvp = new Kvp(idInfo.id(), localVar);

		if(type == Type.i32) {
			impl.add(kvp);
		} else {
			throw new IllegalArgumentException(idInfo.mkString());
		}
		return localVar;
	}

	public LocalVar find(IdInfo idInfo) {
		for(Kvp kvp : impl) {
			if(kvp.key == idInfo.id()) {
				return kvp.value;
			}
		}
		throw new NoSuchElementException(idInfo.mkString());
	}

	static record Kvp(
			int key,
			LocalVar value) {}
}
