package zlk.benchmark;

import org.openjdk.jmh.annotations.*;

public class IntToIntJmhBenchmark {
	@Benchmark
	public Integer run(ZlkJmhState state) throws Throwable {
		return (Integer) state.handle.invokeExact(state.inputs[state.cursor++ & 3]);
	}
}
