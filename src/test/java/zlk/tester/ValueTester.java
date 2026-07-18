package zlk.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Function;

import org.opentest4j.AssertionFailedError;

import zlk.runtime.CustomType;
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

	default void isWrittenIn(String expected) {
		if(this instanceof VData(Object actual)) {
			assertEquals(expected, toWrittenString(actual));
		} else {
			throw new AssertionFailedError("isWrittenIn cannot be used for function");
		}
	}

	private static String toWrittenString(Object value) {
		if(value == null) {
			return "null";
		}
		if(value instanceof Boolean bool) {
			return bool ? "True" : "False";
		}
		if(value instanceof Integer i) {
			return i.toString();
		}

		Class<?> cls = value.getClass();
		Object[] args = valueArgs(value);
		if(args != null) {
			String ctorName = ctorName(cls);
			if(args.length == 0) {
				return ctorName;
			}

			StringBuilder sb = new StringBuilder(ctorName);
			for(Object arg : args) {
				sb.append(' ').append(toWrittenStringAsArg(arg));
			}
			return sb.toString();
		}

		return String.valueOf(value);
	}

	private static String toWrittenStringAsArg(Object value) {
		String string = toWrittenString(value);

		Object[] args = value == null ? null : valueArgs(value);
		if(args != null && args.length != 0) {
			return "(" + string + ")";
		}
		return string;
	}

	private static String ctorName(Class<?> cls) {
		String name = cls.getName();
		int index = name.lastIndexOf('$');
		return index == -1 ? cls.getSimpleName() : name.substring(index + 1);
	}

	private static Object[] valueArgs(Object value) {
		Class<?> cls = value.getClass();
		if(!(value instanceof CustomType) || !cls.isRecord()) {
			return null;
		}

		return Arrays.stream(cls.getRecordComponents())
				.map(component -> {
					try {
						return component.getAccessor().invoke(value);
					} catch(ReflectiveOperationException e) {
						throw new AssertionFailedError(e.toString());
					}
				})
				.toArray();
	}
}
