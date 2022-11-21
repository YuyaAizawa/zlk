package zlk.jvmcode;

import java.util.List;

import zlk.jvmcode.type.JtType;

public record Descriptor(
		List<JtType> args,
		JtType ret
) {
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		args.forEach(ty -> sb.append(ty.descriptor()));
		sb.append(")");
		sb.append(ret.descriptor());
		return sb.toString();
	}
}
