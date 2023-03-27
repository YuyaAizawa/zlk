package zlk.recon;

import java.util.Map;

public final class Substitution {
	private final Map<Integer, TypeSchema> impl;

	public Substitution(Map<Integer, TypeSchema> impl) {
		this.impl = impl;
	}

	public TypeSchema get(TsVar var) {
		int id = var.id();
		TypeSchema result = impl.get(id);
		if(result == null) {
			result = new TsVar(id);
		}
		return result;
	}

	public TypeSchema apply(TypeSchema ty) {
		return ty.fold(
				var   -> get(var),
				base  -> base,
				arrow -> new TsArrow(apply(arrow.arg()), apply(arrow.ret())));
	}
}
