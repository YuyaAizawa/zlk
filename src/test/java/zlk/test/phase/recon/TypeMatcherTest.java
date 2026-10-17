package zlk.test.phase.recon;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import zlk.compiler.ir.typing.Type;
import zlk.compiler.ir.typing.TypeAlias;
import zlk.compiler.phase.recon.TypeMatcher;
import zlk.util.collection.Seq;

public class TypeMatcherTest {
	private static Type.Record record(String tail, Object... fields) {
		var entries = new zlk.util.collection.SeqBuffer<zlk.compiler.ir.typing.RecordField<Type>>();
		for (int i = 0; i < fields.length; i += 2) {
			entries.add(new zlk.compiler.ir.typing.RecordField<>((String) fields[i], (Type) fields[i + 1]));
		}
		return new Type.Record(entries.toSeq(), java.util.Optional.ofNullable(tail).map(Type.RowVar::new));
	}

	@Test
	void rigidTargetsPreserveIndependentAndSharedVariables() {
		Type a = new Type.Var("a"), u = new Type.Var("u"), v = new Type.Var("v");
		var same = record(null, "first", a, "second", a);
		assertFalse(TypeMatcher.matches(same, record(null, "first", u, "second", v)));
		assertTrue(TypeMatcher.matches(same, record(null, "first", u, "second", u)));
		assertFalse(TypeMatcher.matches(record(null, "x", Type.I32), record(null, "x", u)));
		assertEquals(u, TypeMatcher.match(Seq.of(), same,
				record(null, "first", u, "second", u)).orElseThrow().types().get("a"));
	}

	@Test
	void rowResidualsRetainTargetSkolemsAndLacksConstraints() {
		var a = new Type.Var("a");
		var u = new Type.Var("u");
		var hasX = record("r", "x", a);
		var solution = TypeMatcher.match(Seq.of(), hasX, record(null, "x", Type.I32, "y", Type.BOOL)).orElseThrow();
		assertEquals(Type.I32, solution.types().get("a"));
		assertEquals(record(null, "y", Type.BOOL).row(), solution.rows().get("r"));
		var shared = TypeMatcher.match(Seq.of(), hasX, record("s", "x", u, "y", u)).orElseThrow();
		assertEquals(u, shared.types().get("a"));
		assertEquals(record("s", "y", u).row(), shared.rows().get("r"));
		assertFalse(TypeMatcher.matches(hasX, record("s", "y", Type.BOOL)));
		assertFalse(TypeMatcher.matches(record("r", "x", a), record("s", "y", a)));
		var repeated = Type.arrow(record("r", "x", a), record("r", "y", a));
		assertTrue(TypeMatcher.matches(repeated, Type.arrow(record("s", "x", u), record("s", "y", u))));
		assertFalse(TypeMatcher.matches(repeated, Type.arrow(record("s", "x", u), record("t", "y", u))));
		assertFalse(TypeMatcher.matches(repeated, Type.arrow(record(null, "x", u, "y", u), record(null, "y", u))));
	}

	@Test
	void failedPartialBindingsCannotEscapeIntoOtherTrials() {
		var a = new Type.Var("a");
		var target = Type.arrow(Type.BOOL, Type.I32);
		var failing = Type.arrow(a, Type.BOOL);
		var successful = Type.arrow(a, Type.I32);
		var expected = TypeMatcher.match(Seq.of(), successful, target).orElseThrow();
		for (int i = 0; i < 3; i++) {
			assertTrue(TypeMatcher.match(Seq.of(), failing, target).isEmpty());
			assertEquals(expected, TypeMatcher.match(Seq.of(), successful, target).orElseThrow());
		}
		assertEquals("Bool -> I32", target.buildString());
		assertTrue(java.util.stream.IntStream.range(0, 16).parallel()
				.allMatch(_ -> TypeMatcher.match(Seq.of(), successful, target).orElseThrow().equals(expected)));
	}

	@Test
	void nominalIdentityArrowsAndEquivalenceUseTheSameMatcher() {
		var a = new Type.Var("a");
		var b = new Type.Var("b");
		var box = zlk.compiler.id.Id.intern("Main.Box");
		assertTrue(TypeMatcher.matches(new Type.CtorApp(box, Seq.of(a)), new Type.CtorApp(box, Seq.of(Type.I32))));
		assertFalse(TypeMatcher.matches(new Type.CtorApp(box, Seq.of(a)), new Type.CtorApp(zlk.compiler.id.Id.intern("Other.Box"), Seq.of(a))));
		assertTrue(TypeMatcher.equivalent(Type.arrow(a, a), Type.arrow(b, b)));
		assertFalse(TypeMatcher.equivalent(Type.arrow(a, a), Type.arrow(a, b)));
		assertFalse(TypeMatcher.equivalent(a, Type.I32));
	}

	@Test
	void unusedParametersRemainUnboundWithTheirDeclaredKinds() {
		var parameters = Seq.of(new TypeAlias.Parameter("a", TypeAlias.Kind.TYPE),
				new TypeAlias.Parameter("r", TypeAlias.Kind.ROW));
		var solution = TypeMatcher.match(parameters, Type.I32, Type.I32).orElseThrow();
		assertInstanceOf(Type.Var.class, solution.types().get("a"));
		assertTrue(solution.rows().get("r").extension().isPresent());
		assertEquals(2, solution.unboundNames().size());
		assertThrows(UnsupportedOperationException.class, () -> solution.types().clear());
	}
}
