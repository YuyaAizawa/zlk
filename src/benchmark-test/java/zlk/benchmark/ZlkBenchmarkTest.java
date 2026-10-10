package zlk.benchmark;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;

/** 明示的なJUnit選択だけが実測を起動する． */
public abstract class ZlkBenchmarkTest {
	@Test
	public final void benchmark() throws Exception {
		Path output = Path.of("target/benchmarks", getClass().getSimpleName(), java.util.UUID.randomUUID().toString());
		if(Boolean.getBoolean("zlk.benchmark.smoke")) ZlkBenchmarkRunner.smoke(getClass(), output);
		else ZlkBenchmarkRunner.run(getClass(), output);
	}
}
