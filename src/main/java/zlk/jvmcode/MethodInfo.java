package zlk.jvmcode;

import java.util.List;

import zlk.jvmcode.type.JtType;

public record MethodInfo(
		String owner,
		String name,
		Descriptor descriptor
) {
	public MethodInfo(String owner, String name, List<JtType> argTys, JtType retTy) {
		this(owner, name, new Descriptor(argTys, retTy));
	}

	public String descriptorAsString() {
		return descriptor.toString();
	}
}
