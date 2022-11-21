package zlk.jvmcode.type;

public enum Prim implements JtType {
	I,
	Z;

	@Override
	public String descriptor() {
		return name();
	}
}
