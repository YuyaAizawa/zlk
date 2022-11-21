package zlk.jvmcode.type;

public record Obj(
		String name)
implements JtType {

	@Override
	public String descriptor() {
		return "L"+name+";";
	}}
