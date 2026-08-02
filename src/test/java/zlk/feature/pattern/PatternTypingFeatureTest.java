package zlk.feature.pattern;

import org.junit.jupiter.api.Test;

import zlk.fixture.CompilationFixture;

public class PatternTypingFeatureTest {
	@Test
	void partialRecordPatternIsRowPolymorphic() {
		var module = CompilationFixture.compileSucceeded(
				"""
				pick { x = x } = x
				int = pick { x = 1, y = True }
				bool = pick { x = False, z = 2 }
				""");

		module.assertType("int", "I32");
		module.assertType("bool", "Bool");
	}

	@Test
	void emptyRecordPatternInfersAnOpenRecord() {
		var module = CompilationFixture.compileSucceeded(
				"""
				ignore {} = 1
				result = ignore { x = True }
				""");

		module.assertType("ignore", "{ a |  } -> I32");
		module.assertType("result", "I32");
	}
}
