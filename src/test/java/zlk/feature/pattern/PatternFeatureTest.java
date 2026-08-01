package zlk.feature.pattern;

import org.junit.jupiter.api.extension.ExtendWith;
import zlk.tester.DumpOnFailureWatcher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import zlk.common.RecordField;
import zlk.common.Type;
import zlk.tester.ModuleTester;
import zlk.tester.ModuleTester.CompileLevel;
import zlk.tester.ValueTester.VData;
import zlk.util.collection.Seq;

@ExtendWith(DumpOnFailureWatcher.class)
public class PatternFeatureTest {
	@Test
	void matchesNestedRecordPatterns() {
		var module = new ModuleTester("""
				extract { outer = { flag = _, value = value }, tag = _ } = value
				result = extract { tag = True, outer = { value = 42, flag = False } }
				""", CompileLevel.BYTECODE_GEN);

		Type.Var flag = new Type.Var("a");
		Type.Var value = new Type.Var("b");
		Type.Var tag = new Type.Var("d");
		Type.RowVar innerTail = new Type.RowVar("c");
		Type.RowVar outerTail = new Type.RowVar("e");
		module.getType("extract").is(new Type.Arrow(
				new Type.Record(
						Seq.of(
								new RecordField<>("outer", new Type.Record(
										Seq.of(
												new RecordField<>("flag", flag),
												new RecordField<>("value", value)),
										Optional.of(innerTail))),
								new RecordField<>("tag", tag)),
						Optional.of(outerTail)),
				value));
		module.getType("result").is(Type.I32);
		assertEquals(42, ((VData) module.getValue("result")).value());
	}

	@Test
	void checksRefutablePatternsNestedInsideRecords() {
		var module = new ModuleTester("""
				type Option a = None | Some a
				read record =
				  case record of
				    { outer = { value = None } } -> 0
				    { outer = { value = Some value } } -> value
				zero = read { outer = { value = None } }
				one = read { outer = { value = Some 1 } }
				""", CompileLevel.BYTECODE_GEN);

		assertEquals(0, ((VData) module.getValue("zero")).value());
		assertEquals(1, ((VData) module.getValue("one")).value());
	}

	@Test
	void emptyRecordPatternMatchesKnownNonEmptyRecord() {
		var module = new ModuleTester("""
				ignore : { x : I32 } -> I32
				ignore {} = 1
				result = ignore { x = 0 }
				caseResult =
				  case { x = 0 } of
				    {} -> 2
				""", CompileLevel.BYTECODE_GEN);

		module.getType("result").is(Type.I32);
		assertEquals(1, ((VData) module.getValue("result")).value());
		assertEquals(2, ((VData) module.getValue("caseResult")).value());
	}

	@Test
	void partialRecordPatternMatchesKnownWiderRecord() {
		var module = new ModuleTester("""
				pick : { x : I32, y : Bool } -> I32
				pick { x } = x
				result = pick { y = True, x = 3 }
				""", CompileLevel.BYTECODE_GEN);

		module.getType("result").is(Type.I32);
		assertEquals(3, ((VData) module.getValue("result")).value());
	}

	@Test
	void checksPartialRecordBranchesAgainstTheFullKnownShape() {
		var module = new ModuleTester("""
				type Option a = None | Some a
				read : { x : Option I32, y : Bool } -> I32
				read record =
				  case record of
				    { x = None } -> 0
				    { x = Some value, y = _ } -> value
				zero = read { y = True, x = None }
				one = read { y = False, x = Some 1 }
				""", CompileLevel.BYTECODE_GEN);

		assertEquals(0, ((VData) module.getValue("zero")).value());
		assertEquals(1, ((VData) module.getValue("one")).value());
	}
}
