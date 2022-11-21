package zlk.jvmcode;

import java.util.List;

public record ClassDecl(
		String name,
		List<MethodDecl> mothods
) {}
