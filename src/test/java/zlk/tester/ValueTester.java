package zlk.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.function.Function;

import org.opentest4j.AssertionFailedError;

import zlk.tester.ValueTester.VData;
import zlk.tester.ValueTester.VFunction;
import zlk.tester.ValueTester.VMethod;

public sealed interface ValueTester
permits VMethod, VFunction, VData {
	record VMethod(Method method) implements ValueTester {}
	@SuppressWarnings("rawtypes")
	record VFunction(Function function) implements ValueTester {}
	record VData(Object value) implements ValueTester {}

	public static ValueTester of(Object obj) {
		if(obj instanceof Method method) {
			if(method.getParameterCount() == 0) {
				try {
					return new VData(method.invoke(null));
				} catch (ReflectiveOperationException e) {
					throw new Error(e);
				}
			}
			return new VMethod(method);
		}
		if(obj instanceof Function function) {
			return new VFunction(function);
		}
		return new VData(obj);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	default ValueTester apply(Object arg) {
		return switch(this) {
		case VMethod(Method method) -> {
			try {
				if(arg instanceof Integer obj) {
					yield ValueTester.of(method.invoke(null, obj.intValue()));
				}
				yield ValueTester.of(method.invoke(null, arg));
			} catch(ReflectiveOperationException e) {
				throw new AssertionFailedError(e.toString());
			}
		}
		case VFunction(Function function) -> {
			yield ValueTester.of(function.apply(arg));
		}
		case VData(Object _) ->
			throw new AssertionFailedError("data cannot apply");
		};
	}

	default void is(Object expected) {
		if(this instanceof VData(Object actual)) {
			assertEquals(expected, actual);
		} else {
			throw new AssertionFailedError("is cannot be used for function");
		}
	}
}
