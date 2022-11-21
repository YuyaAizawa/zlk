package zlk.jvmcode;

import java.util.List;

import zlk.jvmcode.type.JtType;
import zlk.util.Location;

public record MethodDecl(
		String name,
		List<JtType> argTypes,
		List<String> argNames,
		JtType retType,
		JtExp body,
		Location loc
) {}
