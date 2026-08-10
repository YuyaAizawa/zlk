package zlk.test.phase.nameeval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import zlk.common.id.Id;
import zlk.phase.nameeval.DuplicatedNameException;
import zlk.phase.nameeval.Env;

public class EnvTest {
	@Test
	void withScopeClosesOnNormalExit() {
		Env env = new Env();
		env.withScope("Main", scopeId -> {
			assertEquals(Id.intern("Main"), scopeId);
			register(env, "x");
			assertNotNull(env.getOrNull("x"));
			return null;
		});
		assertNull(env.getOrNull("x"));
	}

	@Test
	void withScopeClosesOnException() {
		Env env = new Env();
		assertThrows(RuntimeException.class, () -> env.withScope("Main", _ -> {
			register(env, "x");
			throw new RuntimeException("boom");
		}));
		assertNull(env.getOrNull("x"));
	}

	@Test
	void withLetFrameSharesScopeWithParent() {
		Env env = new Env();
		env.withScope("Main", _ -> env.withScope("f", scopeId -> {
			env.withLetFrame(() -> {
				assertEquals("Main.f.g", register(env, "g").toString());
				return null;
			});
			assertNull(env.getOrNull("g"));
			assertEquals(Id.intern("Main.f"), scopeId);
			return null;
		}));
	}

	@Test
	void withLetFrameClosesOnException() {
		Env env = new Env();
		env.withScope("Main", _ -> env.withScope("f", _ -> {
			assertThrows(RuntimeException.class, () -> env.withLetFrame(() -> {
				register(env, "g");
				throw new RuntimeException("boom");
			}));
			assertNull(env.getOrNull("g"));
			return null;
		}));
	}

	@Test
	void withLambdaScopeProducesDistinctScope() {
		Env env = new Env();
		env.withScope("Main", _ -> env.withScope("f", _ -> {
			Id lambda1 = env.withLambdaScope(scopeId -> scopeId);
			Id lambda2 = env.withLambdaScope(scopeId -> scopeId);

			assertTrue(lambda1.toString().startsWith("Main.f._lambda"));
			assertTrue(lambda2.toString().startsWith("Main.f._lambda"));
			assertTrue(!lambda1.equals(lambda2));
			return null;
		}));
	}

	@Test
	void withCaseNumbersCasesAndBranchesIndependently() {
		Env env = new Env();
		env.withScope("Main", _ -> env.withScope("f", _ -> {
			Id firstBranch = env.withCase((Env.BranchScopeProvider<Id> branches) ->
					branches.withBranchScope(() -> register(env, "x")));
			Id secondBranch = env.withCase((Env.BranchScopeProvider<Id> branches) -> {
				Id first = branches.withBranchScope(() -> register(env, "x"));
				Id second = branches.withBranchScope(() -> register(env, "y"));
				assertEquals(Id.intern("Main.f._case2_1.x"), first);
				return second;
			});

			assertEquals(Id.intern("Main.f._case1_1.x"), firstBranch);
			assertEquals(Id.intern("Main.f._case2_2.y"), secondBranch);
			return null;
		}));
	}

	@Test
	void nestedCaseDoesNotAdvanceOuterBranchNumber() {
		Env env = new Env();
		env.withScope("Main", _ -> env.withScope("f", _ -> {
			Id outerBranch = env.withCase((Env.BranchScopeProvider<Id> outer) -> {
				Id innerBranch = env.withCase((Env.BranchScopeProvider<Id> inner) ->
						inner.withBranchScope(() -> register(env, "inner")));
				assertEquals(Id.intern("Main.f._case2_1.inner"), innerBranch);
				return outer.withBranchScope(() -> register(env, "outer"));
			});

			assertEquals(Id.intern("Main.f._case1_1.outer"), outerBranch);
			return null;
		}));
	}

	@Test
	void nestedLetFramesDoNotLeak() {
		Env env = new Env();
		env.withScope("Main", _ -> env.withScope("f", _ -> {
			env.withLetFrame(() -> {
				register(env, "a");
				env.withLetFrame(() -> {
					register(env, "b");
					assertNotNull(env.getOrNull("a"));
					assertNotNull(env.getOrNull("b"));
					return null;
				});
				assertNull(env.getOrNull("b"));
				assertNotNull(env.getOrNull("a"));
				return null;
			});
			assertNull(env.getOrNull("a"));
			assertNull(env.getOrNull("b"));
			return null;
		}));
	}

	@Test
	void sameScopeDoesNotReuseNameAfterLetFrameCloses() {
		Env env = new Env();
		env.withScope("Main", _ -> env.withScope("f", _ -> {
			env.withLetFrame(() -> {
				register(env, "local");
				return null;
			});
			env.withLetFrame(() -> {
				assertThrows(DuplicatedNameException.class, () -> env.register("local"));
				return null;
			});
			return null;
		}));
	}

	@Test
	void duplicateReportsPreviouslyAssignedId() {
		Env env = new Env();
		env.withScope("Main", _ -> {
			Id ctorId = Id.intern("Main.Type.Ctor");
			register(env, "Ctor", ctorId);

			DuplicatedNameException ex = assertThrows(
					DuplicatedNameException.class,
					() -> env.register("Ctor"));
			assertEquals(ctorId, ex.oldId);
			assertEquals(Id.intern("Main.Ctor"), ex.newId);
			return null;
		});
	}

	@Test
	void nestedScopeRestoresParentScope() {
		Env env = new Env();
		env.withScope("Main", scopeId -> {
			env.withScope("f", nestedScopeId -> {
				assertEquals(Id.intern("Main.f"), nestedScopeId);
				return null;
			});
			assertEquals(Id.intern("Main"), scopeId);
			env.withScope("g", restoredScopeId -> {
				assertEquals(Id.intern("Main.g"), restoredScopeId);
				return null;
			});
			return null;
		});
	}

	private static Id register(Env env, String name) {
		try {
			return env.register(name);
		} catch (DuplicatedNameException e) {
			throw new AssertionError(e);
		}
	}

	private static Id register(Env env, String name, Id id) {
		try {
			return env.register(name, id);
		} catch (DuplicatedNameException e) {
			throw new AssertionError(e);
		}
	}
}
