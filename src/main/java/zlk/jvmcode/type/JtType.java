package zlk.jvmcode.type;

public sealed interface JtType
permits Prim, Obj {
	public String descriptor();
}
