package zlk.benchmark;

import zlk.runtime.ZlkRecord;

/** 組込み題材の独立した期待値検査．frameworkはSubjectを知らない． */
public final class BenchmarkVerification {
	private BenchmarkVerification() {}
	public static void verify(CompiledBenchmark compiled, BenchmarkSources.Sample sample) throws Throwable {
		var handle = compiled.handle();
		for(Integer input : new Integer[] {17, 257, -31, 1009, Integer.MAX_VALUE}) {
			if(sample.fields() == 0) {
				Integer actual = (Integer) handle.invokeExact(input);
				int expected = input;
				if(sample.subject() == BenchmarkSources.Subject.NAMED_LOCAL) {
					int sum = input + input;
					expected = sum * sum;
				} else for(int i = 0; i < sample.depth(); i++) expected += input * input;
				check(expected, actual);
			} else {
				ZlkRecord actual = (ZlkRecord) handle.invokeExact(input);
				if(sample.subject() == BenchmarkSources.Subject.RECORD_SHARED) {
					ZlkRecord original = (ZlkRecord) actual.get("original");
					for(int f = 0; f < sample.fields(); f++) check(input + f + 1, original.get("f" + f));
					actual = (ZlkRecord) actual.get("changed");
				}
				int[] expected = new int[sample.fields()];
				for(int f = 0; f < expected.length; f++) expected[f] = input + f + 1;
				for(int u = 0; u < sample.updates(); u++) for(int j = 0; j < sample.updatedFields(); j++) {
					int f = (u * sample.updatedFields() + j) % sample.fields();
					expected[f] = sample.subject() == BenchmarkSources.Subject.COMBINED
							? input * input + input + u + f + 1 : input + u + 1;
				}
				for(int f = 0; f < expected.length; f++) check(expected[f], actual.get("f" + f));
			}
		}
	}
	private static void check(int expected, Object actual) {
		if(!Integer.valueOf(expected).equals(actual)) throw new IllegalStateException("expected " + expected + ", got " + actual);
	}
}
