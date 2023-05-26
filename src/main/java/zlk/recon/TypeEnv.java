package zlk.recon;

import java.util.List;

import zlk.common.id.Id;
import zlk.util.AssocList;

public class TypeEnv extends AssocList<Id, TypeSchema> {

	public boolean containsVar(Id id) {
		return containsKey(id);
	}

	public List<Id> domain() {
		return keys();
	}

	public void add(Id var, TypeSchema ty) {
		push(var, ty);
	}

	public void addIfNotContains(Id var, TypeSchema ty) {
		if(!containsVar(var)) {
			add(var, ty);
		}
	}
}
