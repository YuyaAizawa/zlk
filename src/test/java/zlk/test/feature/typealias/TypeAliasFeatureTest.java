package zlk.test.feature.typealias;

import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import zlk.util.fixture.CompilationFixture;
import zlk.util.tester.DumpOnFailureWatcher;

@ExtendWith(DumpOnFailureWatcher.class)
public class TypeAliasFeatureTest {
	@Test
	void aliasEndToEndResolvesToClosedRecordAndGeneratesNoAliasClass() {
		String src =
				"""
				type alias Foo a = { a | x : I32, y : Bool }
				bar : Foo { z : I32 } -> I32
				bar record = add record.x record.z
				result = bar { x = 1, y = True, z = 2 }
				""";

		var module = CompilationFixture.compileSucceeded(src);

		// bar resolved typeはclosed { x:I32, y:Bool, z:I32 } -> I32
		module.assertType("bar", "{ x : I32, y : Bool, z : I32 } -> I32");

		// result実行値3
		int result = (int) module.value("result");
		assertEquals(3, result);

		// alias用 Main$Foo classは生成されない
		assertFalse(module.generatedClassNames().contains("Main$Foo"));
	}
}
