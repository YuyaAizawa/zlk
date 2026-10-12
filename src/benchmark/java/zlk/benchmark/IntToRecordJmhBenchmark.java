package zlk.benchmark;

import org.openjdk.jmh.annotations.*;
import zlk.runtime.ZlkRecord;

public class IntToRecordJmhBenchmark {
	@Benchmark
	public ZlkRecord run(ZlkJmhState state) throws Throwable {
		return (ZlkRecord) state.handle.invokeExact(state.inputs[state.cursor++ & 3]);
	}
}
