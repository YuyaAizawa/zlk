package zlk.benchmark;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import zlk.compiler.CompilationOptions;

/** public static生成関数を解釈する，benchmark専用reflection境界． */
public final class BenchmarkDefinition {
	private final Class<?> type;
	private final Method source;
	private final Method options;
	private final Method verify;
	private final Map<String, Object[]> sources;
	private final Map<String, Object[]> settings;
	private BenchmarkDefinition(Class<?> type) {
		this.type = type;
		if(!Modifier.isPublic(type.getModifiers()) || type.getAnnotation(ZlkBenchmark.class) == null)
			throw new IllegalArgumentException("public @ZlkBenchmark required: " + type.getName());
		var config = configuration();
		if(config.forks() < 1 || config.warmupIterations() < 1 || config.measurementIterations() < 1 || config.seconds() < 1)
			throw new IllegalArgumentException("positive fork/iteration/time settings required: " + type.getName());
		source = generator(Source.class, String.class, true);
		options = generator(Options.class, CompilationOptions.class, false);
		Class<?>[] verifyTypes = new Class<?>[source.getParameterCount() + 1];
		verifyTypes[0] = CompiledBenchmark.class;
		System.arraycopy(source.getParameterTypes(), 0, verifyTypes, 1, source.getParameterCount());
		try { verify = type.getMethod("verify", verifyTypes); }
		catch(NoSuchMethodException e) { throw new IllegalArgumentException("verify signature required: " + type.getName(), e); }
		if(!Modifier.isStatic(verify.getModifiers()) || verify.getReturnType() != void.class)
			throw new IllegalArgumentException("public static void verify required: " + type.getName());
		sources = arguments(source);
		settings = options == null ? Collections.singletonMap("fixed", new Object[0]) : arguments(options);
	}
	private Method generator(Class<? extends java.lang.annotation.Annotation> annotation, Class<?> result, boolean required) {
		var methods = Arrays.stream(type.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(annotation)).toList();
		if(methods.size() > 1 || required && methods.isEmpty())
			throw new IllegalArgumentException("expected " + (required ? "one" : "zero or one") + " @" + annotation.getSimpleName() + ": " + type.getName());
		if(methods.isEmpty()) return null;
		Method method = methods.getFirst();
		if(!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers()) || method.getReturnType() != result)
			throw new IllegalArgumentException("invalid generator: " + method);
		return method;
	}
	public static BenchmarkDefinition of(Class<?> type) { return new BenchmarkDefinition(type); }

	public ZlkBenchmark configuration() { return type.getAnnotation(ZlkBenchmark.class); }
	public String name() { return type.getName(); }
	public List<ReportChart> charts() { return List.of(type.getAnnotationsByType(ReportChart.class)); }
	public JmhChart jmhChart() { return type.getAnnotation(JmhChart.class); }
	/** case文字列を再解釈せず，定義境界で検査済みの引数をreportへ渡す． */
	public Map<String, String> parameters(BenchmarkCase item) {
		if(!item.definitionClass().equals(type.getName()) || !sources.containsKey(item.sourceCase()) || !settings.containsKey(item.optionCase()))
			throw new IllegalArgumentException("case does not belong to definition: " + item);
		Map<String, String> result = new LinkedHashMap<>();
		putParameters(result, "source.", source, sources.get(item.sourceCase()));
		if(options != null) putParameters(result, "option.", options, settings.get(item.optionCase()));
		return Collections.unmodifiableMap(result);
	}
	private static void putParameters(Map<String, String> out, String prefix, Method method, Object[] args) {
		for(int i = 0; i < args.length; i++) out.put(prefix + method.getParameters()[i].getName(), args[i].toString());
	}
	public List<BenchmarkCase> cases() {
		List<BenchmarkCase> result = new ArrayList<>();
		Map<String, String> hashes = new HashMap<>();
		for(String src : sources.keySet()) for(String opts : settings.keySet()) {
			var item = resolve(src, opts);
			String hash = BenchmarkMetadata.sha256(item.source());
			String previous = hashes.putIfAbsent(src, hash);
			if(previous != null && !previous.equals(hash))
				throw new IllegalArgumentException("source changes across options: " + type.getName() + " [" + src + "] [" + opts + "]");
			result.add(item);
		}
		return List.copyOf(result);
	}
	public BenchmarkCase resolve(String src, String opts) {
		if(!sources.containsKey(src) || !settings.containsKey(opts))
			throw new IllegalArgumentException("unknown case: " + type.getName() + " [" + src + "] [" + opts + "]");
		String context = type.getName() + " [" + src + "] [" + opts + "]";
		return new BenchmarkCase(type.getName(), src, opts,
				(String) invoke(source, sources.get(src), context),
				options == null ? CompilationOptions.DEFAULT : (CompilationOptions) invoke(options, settings.get(opts), context));
	}
	public void verify(BenchmarkCase item, CompiledBenchmark compiled) {
		if(!item.definitionClass().equals(type.getName()) || !sources.containsKey(item.sourceCase()) || !settings.containsKey(item.optionCase()))
			throw new IllegalArgumentException("case does not belong to definition: " + item);
		Class<?> result = switch(configuration().shape()) {
			case INT_TO_INT -> Integer.class;
			case INT_TO_RECORD -> zlk.runtime.ZlkRecord.class;
		};
		if(!compiled.handle().type().equals(java.lang.invoke.MethodType.methodType(result, Integer.class)))
			throw new IllegalArgumentException("unexpected ABI: " + compiled.handle().type() + " " + item.definitionClass());
		Object[] args = new Object[source.getParameterCount() + 1];
		args[0] = compiled;
		System.arraycopy(sources.get(item.sourceCase()), 0, args, 1, source.getParameterCount());
		invoke(verify, args, type.getName() + " [" + item.sourceCase() + "] [" + item.optionCase() + "]");
	}
	private static Object invoke(Method method, Object[] args, String context) {
		try {
			Object result = method.invoke(null, args);
			if(result == null && method.getReturnType() != void.class) throw new IllegalArgumentException("null generator result: " + context);
			return result;
		} catch(InvocationTargetException e) { throw new IllegalArgumentException("definition function failed: " + context, e.getCause()); }
		catch(IllegalAccessException e) { throw new IllegalArgumentException("cannot access definition: " + context, e); }
	}
	private static Map<String, Object[]> arguments(Method method) {
		Map<String, Object[]> result = new LinkedHashMap<>();
		expand(method.getParameters(), 0, new ArrayList<>(), new ArrayList<>(), result);
		return Collections.unmodifiableMap(result);
	}
	private static void expand(Parameter[] params, int index, List<String> names, List<Object> args, Map<String, Object[]> result) {
		if(index == params.length) { result.put(names.isEmpty() ? "fixed" : String.join(";", names), args.toArray()); return; }
		Parameter p = params[index];
		if(!p.isNamePresent() || p.getAnnotation(Values.class) == null || p.getAnnotation(Values.class).value().length == 0)
			throw new IllegalArgumentException("named parameter with nonempty @Values required: " + p);
		if(p.getType() != int.class && p.getType() != boolean.class)
			throw new IllegalArgumentException("only int/boolean supported: " + p);
		Set<Object> seen = new HashSet<>();
		for(String text : p.getAnnotation(Values.class).value()) {
			if(p.getType() == boolean.class && !text.equals("false") && !text.equals("true"))
				throw new IllegalArgumentException("strict boolean required: " + text);
			Object value = p.getType() == int.class ? Integer.valueOf(text) : Boolean.valueOf(text);
			if(!seen.add(value)) throw new IllegalArgumentException("duplicate value: " + p);
			names.add(p.getName() + "=" + text); args.add(value);
			expand(params, index + 1, names, args, result);
			names.removeLast(); args.removeLast();
		}
	}
}
