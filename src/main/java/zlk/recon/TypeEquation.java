package zlk.recon;

public final class TypeEquation {
	TypeSchema left;
	TypeSchema right;

	public TypeEquation(TypeSchema left, TypeSchema right) {
		this.left = left;
		this.right = right;
	}

	public void substitute(TsVar var, TypeSchema type) {
		left = substitute(left, var, type);
		right = substitute(right, var, type);
	}

	private static TypeSchema substitute(TypeSchema target, TsVar var, TypeSchema type) {
		if(!target.contains(var)) {
			return target;
		}
		return target.fold(
				tVar -> tVar.equals(var) ? type : tVar,
				base -> base,
				arrow -> new TsArrow(
						substitute(arrow.arg(), var, type),
						substitute(arrow.ret(), var, type)));
	}
}
