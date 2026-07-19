package zlk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import zlk.runtime.ArrayRecord;
import zlk.runtime.ZlkRecord;

class RecordTest {
	private record Pair(int x, int y) {}

	private static final class PairRecord implements ZlkRecord {
		private final Pair value;

		private PairRecord(Pair value) {
			this.value = value;
		}

		@Override
		public List<String> names() {
			return List.of("x", "y");
		}

		@Override
		public Object get(String name) {
			return switch(name) {
			case "x" -> value.x();
			case "y" -> value.y();
			default -> throw new IllegalArgumentException("unknown field: " + name);
			};
		}

		@Override
		public ZlkRecord update(String name, Object newValue) {
			return switch(name) {
			case "x" -> new PairRecord(new Pair((Integer) newValue, value.y()));
			case "y" -> new PairRecord(new Pair(value.x(), (Integer) newValue));
			default -> throw new IllegalArgumentException("unknown field: " + name);
			};
		}

		@Override
		public boolean equals(Object other) {
			return ZlkRecord.structuralEquals(this, other);
		}

		@Override
		public int hashCode() {
			return ZlkRecord.structuralHashCode(this);
		}

		@Override
		public String toString() {
			return ZlkRecord.structuralToString(this);
		}
	}

	@Test
	void namesReturnsImmutableListInCanonicalOrder() {
		ZlkRecord record = ZlkRecord.of(Map.of("y", 2, "x", 1));

		List<String> names = record.names();

		assertEquals(List.of("x", "y"), names);
		assertThrows(UnsupportedOperationException.class, () -> names.add("z"));
		assertThrows(UnsupportedOperationException.class, () -> names.set(0, "z"));
	}

	@Test
	void getReadsFieldByName() {
		ZlkRecord record = ZlkRecord.of(Map.of("y", 2, "x", 1));

		assertEquals(1, record.get("x"));
		assertEquals(2, record.get("y"));
	}

	@Test
	void getRejectsUnknownField() {
		ZlkRecord record = ZlkRecord.of(Map.of("x", 1));

		assertThrows(IllegalArgumentException.class, () -> record.get("y"));
	}

	@Test
	void updateReturnsNewValueAndPreservesOriginal() {
		ZlkRecord original = ZlkRecord.of(Map.of("x", 1, "y", 2));

		ZlkRecord updated = original.update("y", 3);

		assertEquals(2, original.get("y"));
		assertEquals(1, updated.get("x"));
		assertEquals(3, updated.get("y"));
		assertEquals(original.names(), updated.names());
	}

	@Test
	void updateRejectsUnknownField() {
		ZlkRecord record = ZlkRecord.of(Map.of("x", 1));

		assertThrows(IllegalArgumentException.class, () -> record.update("y", 2));
	}

	@Test
	void structuralValueSemanticsCrossImplementations() {
		ZlkRecord array = ZlkRecord.of(Map.of("x", 1, "y", 2));
		ZlkRecord wrapper = new PairRecord(new Pair(1, 2));

		assertTrue(array.equals(wrapper));
		assertTrue(wrapper.equals(array));
		assertEquals(array.hashCode(), wrapper.hashCode());
		assertEquals("{ x = 1, y = 2 }", array.toString());
		assertEquals(array.toString(), wrapper.toString());
	}

	@Test
	void structuralRenderingUsesZlkValueRulesRecursively() {
		ZlkRecord nested = ZlkRecord.of(Map.of(
				"flag", true,
				"pair", ZlkRecord.of(Map.of("x", 1, "y", 2))));

		assertEquals("{ flag = True, pair = { x = 1, y = 2 } }", nested.toString());
	}

	@Test
	void javaFactoryDoesNotRetainMapState() {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("x", 1);
		ZlkRecord record = ZlkRecord.of(fields);

		fields.put("x", 2);
		fields.put("y", 3);

		assertEquals(List.of("x"), record.names());
		assertEquals(1, record.get("x"));
	}

	@Test
	void emptyRecordsHaveTheSameValue() {
		ZlkRecord first = ZlkRecord.of(Map.of());
		ZlkRecord second = ZlkRecord.of(Map.of());

		assertEquals(first, second);
		assertEquals(List.of(), first.names());
		assertEquals("{}", first.toString());
	}

	@Test
	void bootstrapCreatesRecordsForTheCanonicalShape() throws Throwable {
		CallSite site = ArrayRecord.bootstrapLiteral(
				MethodHandles.lookup(),
				"recordLiteral",
				MethodType.methodType(ZlkRecord.class, Object.class, Object.class),
				"v1;1#x1#y");

		ZlkRecord first = (ZlkRecord) site.dynamicInvoker().invokeExact((Object) 1, (Object) 2);
		ZlkRecord second = (ZlkRecord) site.dynamicInvoker().invokeExact((Object) 3, (Object) 4);

		assertEquals(List.of("x", "y"), first.names());
		assertEquals(1, first.get("x"));
		assertEquals(2, first.get("y"));
		assertEquals(3, second.get("x"));
		assertEquals(4, second.get("y"));
	}

	@Test
	void bootstrapCreatesEmptyRecord() throws Throwable {
		CallSite site = ArrayRecord.bootstrapLiteral(
				MethodHandles.lookup(),
				"recordLiteral",
				MethodType.methodType(ZlkRecord.class),
				"v1;");

		ZlkRecord first = (ZlkRecord) site.dynamicInvoker().invokeExact();
		ZlkRecord second = (ZlkRecord) site.dynamicInvoker().invokeExact();
		assertEquals(first, second);
		assertEquals(ZlkRecord.of(Map.of()), first);
		assertEquals(List.of(), first.names());
	}

	@Test
	void bootstrapRejectsInvalidShape() {
		assertThrows(IllegalArgumentException.class, () -> ArrayRecord.bootstrapLiteral(
				MethodHandles.lookup(),
				"recordLiteral",
				MethodType.methodType(ZlkRecord.class, Object.class, Object.class),
				"v1;1#y1#x"));
		assertThrows(IllegalArgumentException.class, () -> ArrayRecord.bootstrapLiteral(
				MethodHandles.lookup(),
				"recordLiteral",
				MethodType.methodType(ZlkRecord.class, Object.class),
				"broken"));
	}

	@Test
	void generatedInvokeDynamicCreatesRecordLiteral() throws ReflectiveOperationException {
		String className = "zlk/runtime/GeneratedRecordLiteral";
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
		cw.visit(Opcodes.V23, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, className, null,
				"java/lang/Object", null);
		MethodVisitor mv = cw.visitMethod(
				Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
				"make",
				"(Ljava/lang/Object;Ljava/lang/Object;)Lzlk/runtime/ZlkRecord;",
				null,
				null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitInvokeDynamicInsn(
				"recordLiteral",
				"(Ljava/lang/Object;Ljava/lang/Object;)Lzlk/runtime/ZlkRecord;",
				new Handle(
						Opcodes.H_INVOKESTATIC,
						"zlk/runtime/ArrayRecord",
						"bootstrapLiteral",
						"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
						+ "Ljava/lang/invoke/MethodType;Ljava/lang/String;)Ljava/lang/invoke/CallSite;",
						false),
				"v1;1#x1#y");
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		cw.visitEnd();

		class Loader extends ClassLoader {
			Loader() {
				super(ArrayRecord.class.getClassLoader());
			}

			Class<?> define(byte[] bytes) {
				return defineClass(className.replace('/', '.'), bytes, 0, bytes.length);
			}
		}
		Class<?> generated = new Loader().define(cw.toByteArray());
		Object value = generated.getMethod("make", Object.class, Object.class).invoke(null, 1, 2);

		assertEquals(ZlkRecord.of(Map.of("x", 1, "y", 2)), value);
	}
}
