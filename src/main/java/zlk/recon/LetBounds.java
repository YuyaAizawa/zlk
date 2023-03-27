package zlk.recon;

import java.util.NoSuchElementException;

import zlk.common.id.Id;
import zlk.util.AssocList;

public class LetBounds extends AssocList<Id, TypeSchema> {

	public void add(Id id, TypeSchema ty) {
		push(id, ty);
	}

	public TypeSchema freshInst(Id id) {
		TypeSchema ty = get(id);
		if(ty == null) {
			throw new NoSuchElementException(id.toString());
		}
		return freshInst(ty);
	}

	private static TypeSchema freshInst(TypeSchema ty) {
		return ty.fold(
			var -> new TsVar(),
			base -> base,
			arrow -> new TsArrow(freshInst(arrow.arg()), freshInst(arrow.ret())));
	}
}
