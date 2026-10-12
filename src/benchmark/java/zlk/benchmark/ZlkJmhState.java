package zlk.benchmark;

import java.lang.invoke.MethodHandle;
import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
public class ZlkJmhState {
	@Param({"unset"}) public String definitionClass;
	@Param({"fixed"}) public String sourceCase;
	@Param({"fixed"}) public String optionCase;
	protected MethodHandle handle;
	protected final Integer[] inputs = {17, 257, -31, 1009};
	protected int cursor;
	@Setup(Level.Trial)
	public void setup() throws Throwable {
		Class<?> definition = Class.forName(definitionClass);
		var parsed = BenchmarkDefinition.of(definition);
		var item = parsed.resolve(sourceCase, optionCase);
		var compiled = CompiledBenchmark.compile(item.source(), item.options());
		parsed.verify(item, compiled);
		handle = compiled.handle();
		cursor = 0;
	}
}
