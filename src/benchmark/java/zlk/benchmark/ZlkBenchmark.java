package zlk.benchmark;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ZlkBenchmark {
	InvocationShape shape();
	boolean gc() default true;
	int forks() default 3;
	int warmupIterations() default 5;
	int measurementIterations() default 5;
	int seconds() default 1;
}
