package zlk.ast;

import zlk.common.Type;

public record Decl(
		String name,
		Type type,
		Exp body) {}
