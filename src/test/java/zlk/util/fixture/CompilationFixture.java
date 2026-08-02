package zlk.util.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.AccessFlag;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Function;

import zlk.common.id.Id;
import zlk.compiler.Driver;
import zlk.diagnostic.Diagnostic;
import zlk.util.collection.Seq;
import zlk.util.tester.DumpOnFailureWatcher;

/** Driverの型情報出力を有効にしたtest用コンパイル結果． */
public final class CompilationFixture {
	private static final Driver.CompilationOptions REPORT_TYPES =
			new Driver.CompilationOptions(true);

	private final Driver.CompilationResult result;
	private final Seq<String> generatedClassNames;
	private final InMemoryClassLoader classLoader;

	private CompilationFixture(Driver.CompilationResult result) {
		this.result = result;
		if(result instanceof Driver.CompilationResult.Succeeded succeeded) {
			Map<String, byte[]> clazzes = succeeded.clazzes();
			clazzes.forEach(DumpOnFailureWatcher::setLastClassDump);
			this.generatedClassNames = Seq.from(clazzes.keySet());
			this.classLoader = new InMemoryClassLoader(clazzes);
		} else {
			this.generatedClassNames = Seq.of();
			this.classLoader = new InMemoryClassLoader(Map.of());
		}
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

	public Object value(String declaration, Object... arguments) {
		Object current = getMainClassMethod(declaration);
		for(Object argument : arguments) {
			current = apply(current, argument);
		}
		if(current instanceof Method method && method.getParameterCount() == 0) {
			current = invoke(method);
		}
		if(current instanceof Method || current instanceof Function<?, ?>) {
			return fail("value remains a function: Main." + declaration);
		}
		return current;
	}

	public Seq<String> generatedClassNames() {
		return generatedClassNames;
	}

	private Method getMainClassMethod(String name) {
		Class<?> main;
		try {
			main = classLoader.loadClass("Main");
		} catch(ClassNotFoundException e) {
			return fail("generated Main class not found", e);
		}

		Seq<Method> matches = Seq.of(main.getDeclaredMethods())
				.filter(method -> method.getName().equals(name))
				.filter(method -> method.accessFlags().contains(AccessFlag.PUBLIC))
				.filter(method -> method.accessFlags().contains(AccessFlag.STATIC));
		if(matches.size() != 1) {
			return fail(
					"expected exactly one public static method named " + name
							+ " in Main, but found " + matches.size());
		}
		return matches.head();
	}

	@SuppressWarnings({ "unchecked" })
	private static Object apply(Object function, Object argument) {
		if(function instanceof Method method) {
			return invoke(method, argument);
		}
		if(function instanceof Function fn) {
			return fn.apply(argument);
		}
		return fail("data cannot be applied: " + function);
	}

	private static Object invoke(Method method, Object... arguments) {
		try {
			return method.invoke(null, arguments);
		} catch(InvocationTargetException e) {
			return fail("generated method threw: " + method.getName(), e.getCause());
		} catch(ReflectiveOperationException | IllegalArgumentException e) {
			return fail("failed to invoke generated method: " + method.getName(), e);
		}
	}
}

final class InMemoryClassLoader extends ClassLoader {
	private final Map<String, byte[]> clazzes;

	InMemoryClassLoader(Map<String, byte[]> clazzes) {
		this.clazzes = Map.copyOf(clazzes);
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		byte[] bytecode = clazzes.get(name);
		if(bytecode == null) {
			throw new ClassNotFoundException(name);
		}
		return defineClass(name, bytecode, 0, bytecode.length);
	}
}
