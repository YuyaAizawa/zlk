package zlk.feature.typealias;

import org.junit.jupiter.api.extension.ExtendWith;
import zlk.tester.DumpOnFailureWatcher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import zlk.common.RecordField;
import zlk.common.Type;
import zlk.tester.ModuleTester;
import zlk.tester.ModuleTester.CompileLevel;
import zlk.tester.ValueTester.VData;
import zlk.util.collection.Seq;

@ExtendWith(DumpOnFailureWatcher.class)
public class TypeAliasFeatureTest {
	@Test
	void aliasEndToEndResolvesToClosedRecordAndGeneratesNoAliasClass() throws ReflectiveOperationException {
		String src =
				"""
				type alias Foo a = { a | x : I32, y : Bool }
				bar : Foo { z : I32 } -> I32
				bar record = add record.x record.z
				result = bar { x = 1, y = True, z = 2 }
				""";

		var module = new ModuleTester(src, CompileLevel.BYTECODE_GEN);

		// bar resolved typeはclosed { x:I32, y:Bool, z:I32 } -> I32
		Type.Record expectedArg = new Type.Record(Seq.of(
				new RecordField<>("x", Type.I32),
				new RecordField<>("y", Type.BOOL),
				new RecordField<>("z", Type.I32)));
		module.getType("bar").is(new Type.Arrow(expectedArg, Type.I32));

		// result実行値3
		assertEquals(3, ((VData) module.getValue("result")).value());

		// alias用 Main$Foo classは生成されない
		assertFalse(module.getGeneratedClassNames().contains("Main$Foo"));

		// bar reflection parameter typeは zlk.runtime.ZlkRecord
		java.lang.reflect.Method barMethod = module.getMainMethod("bar");
		Class<?>[] paramTypes = barMethod.getParameterTypes();
		assertEquals(1, paramTypes.length);
		assertEquals(zlk.runtime.ZlkRecord.class, paramTypes[0]);
	}
}
