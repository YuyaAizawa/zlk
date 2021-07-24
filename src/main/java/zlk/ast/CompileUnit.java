package zlk.ast;

import java.util.List;

public record CompileUnit(
		String name,
		List<Decl> decls) {}
