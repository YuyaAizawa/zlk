package zlk.util.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.compiler.Driver;
import zlk.diagnostic.Diagnostic;
import zlk.util.collection.Seq;

/** Driverの型情報出力を有効にしたtest用コンパイル結果． */
public final class CompilationFixture {
	private static final Driver.CompilationOptions REPORT_TYPES =
			new Driver.CompilationOptions(true);

	private final Driver.CompilationResult result;
	private final IdMap<Type> types;

	private CompilationFixture(Driver.CompilationResult result) {
		this.result = result;
		this.types = new IdMap<>();
	}

	public static CompilationFixture compile(String src) {
		return new CompilationFixture(Driver.compile("Main.zlk", "module Main\n" + src, REPORT_TYPES));
	}

	public static CompilationFixture compileSucceeded(String src) {
		CompilationFixture compilation = compile(src);
		assertInstanceOf(
				Driver.CompilationResult.Succeeded.class,
				compilation.result,
				"compilation failed:\n" + compilation.result.diags().join(System.lineSeparator()));
		return compilation;
	}

	public Driver.CompilationResult result() {
		return result;
	}

	public Seq<Diagnostic> diagnostics() {
		return result.diags();
	}

	public <T extends Diagnostic> Seq<T> diagnostics(Class<T> type) {
		return result.diags()
				.filter(type::isInstance)
				.map(type::cast);
	}

	public Seq<Diagnostic.InferredType> inferredTypes() {
		return diagnostics(Diagnostic.InferredType.class);
	}

	public void assertType(String declaration, String expected) {
		Id id = Id.intern("Main." + declaration);
		Seq<Diagnostic.InferredType> matches = inferredTypes()
				.filter(info -> info.declaration().equals(id));
		String available = inferredTypes()
				.map(info -> info.declaration().canonicalName() + " : " + info.type().buildString())
				.join(", ");
		assertEquals(
				1,
				matches.size(),
				() -> "expected exactly one inferred type for " + id
						+ ", but found " + matches.size()
						+ ". available inferred types: [" + available + "]");
		assertEquals(
				expected,
				matches.head().type().buildString(),
				() -> "unexpected inferred type for " + id);
	}
}
