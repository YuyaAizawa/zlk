package zlk.benchmark;

@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
public class ForkDefinitionFixture {
	@Source public static String source() { return "module Bench\nbench x = add x x\n"; }
	public static void verify(CompiledBenchmark compiled) throws Throwable {
		Integer value = (Integer) compiled.handle().invokeExact(Integer.valueOf(17));
		if(value != 34) throw new IllegalStateException("fork result");
	}
}
