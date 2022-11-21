package zlk.jvmcode;

public record MethodInfo(
		String owner,
		String name,
		Descriptor descriptor
) {
	public String descriptorAsString() {
		return descriptor.toString();
	}
}
