package zlk.benchmark;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import org.objectweb.asm.Type;
import zlk.compiler.CompilationOptions;
import zlk.compiler.driver.Driver;

/** ベンチマーク専用のコンパイル結果と，完全なbytecode集合を持つloader． */
public record CompiledBenchmark(Map<String, byte[]> classes, MethodHandle handle, String descriptor) {
	public static CompiledBenchmark compile(String source, CompilationOptions options) {
		return switch(Driver.compile("Bench.zlk", source, options)) {
		case Driver.CompilationResult.Failed failed ->
			throw new IllegalArgumentException("compilation failed: " + failed.diags().join("\n"));
		case Driver.CompilationResult.Succeeded succeeded -> {
			Map<String, byte[]> classes = Map.copyOf(succeeded.clazzes());
			ClassLoader loader = new ClassLoader(CompiledBenchmark.class.getClassLoader()) {
				@Override
				protected Class<?> findClass(String name) throws ClassNotFoundException {
					byte[] bytes = classes.get(name);
					if(bytes == null) throw new ClassNotFoundException(name);
					return defineClass(name, bytes, 0, bytes.length);
				}
			};
			try {
				var methods = Arrays.stream(loader.loadClass("Bench").getDeclaredMethods())
						.filter(m -> m.getName().equals("bench"))
						.filter(m -> Modifier.isPublic(m.getModifiers()) && Modifier.isStatic(m.getModifiers()))
						.toList();
				if(methods.size() != 1) throw new IllegalArgumentException("expected one public static bench");
				var method = methods.getFirst();
				yield new CompiledBenchmark(classes, MethodHandles.publicLookup().unreflect(method), Type.getMethodDescriptor(method));
			} catch(ReflectiveOperationException e) {
				throw new IllegalStateException("cannot link generated bench", e);
			}
		}
		};
	}
}
