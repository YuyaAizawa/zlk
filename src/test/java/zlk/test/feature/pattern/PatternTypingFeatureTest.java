package zlk.test.feature.pattern;

import org.junit.jupiter.api.Test;

import zlk.util.fixture.CompilationFixture;

public class PatternTypingFeatureTest {
	@Test
	void partialRecordPatternIsRowPolymorphic() {
		var module = CompilationFixture.compileSucceeded(
				"""
				pick { x } = x
				int = pick { x = 1, y = True }
				bool = pick { x = False, z = 2 }
				""");

		module.assertType("int", "I32");
		module.assertType("bool", "Bool");
	}

}
