package zlk.phase.nameeval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import zlk.common.id.Id;

public class EnvTest {
	@Test
	void withScopeClosesOnNormalExit() {
		Env env = new Env();
		env.withScope("Main", owner -> {
			assertEquals(Id.intern("Main"), owner);
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
	void withLetFrameSharesOwnerWithParent() {
		Env env = new Env();
		env.withScope("Main", _ -> env.withScope("f", owner -> {
			env.withLetFrame(() -> {
				assertEquals("Main.f.g", register(env, "g").toString());
				return null;
			});
			assertNull(env.getOrNull("g"));
			assertEquals(Id.intern("Main.f"), owner);
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
	void withLambdaScopeProducesDistinctOwner() {
		Env env = new Env();
		env.withScope("Main", _ -> env.withScope("f", _ -> {
			Id lambda1 = env.withLambdaScope(owner -> owner);
			Id lambda2 = env.withLambdaScope(owner -> owner);

			assertTrue(lambda1.toString().startsWith("Main.f._lambda"));
			assertTrue(lambda2.toString().startsWith("Main.f._lambda"));
			assertTrue(!lambda1.equals(lambda2));
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
	void sameOwnerDoesNotReuseNameAfterLetFrameCloses() {
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
	void nestedScopeRestoresParentOwner() {
		Env env = new Env();
		env.withScope("Main", owner -> {
			env.withScope("f", nestedOwner -> {
				assertEquals(Id.intern("Main.f"), nestedOwner);
				return null;
			});
			assertEquals(Id.intern("Main"), owner);
			env.withScope("g", restoredOwner -> {
				assertEquals(Id.intern("Main.g"), restoredOwner);
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
