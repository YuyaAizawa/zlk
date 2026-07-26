package zlk.nameeval;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import zlk.common.id.Id;

/**
 * Env の lexical binding frame／Id owner 分離，scope lifetime の例外安全性，
 * 内部状態隠蔽を直接検証する．
 */
class EnvTest {

	@Test
	void withScopeClosesOnNormalExit() {
		Env env = new Env();
		env.withScope("Main", () -> {
			register(env, "x");
			assertNotNull(env.getOrNull("x"));
			return null;
		});
		assertNull(env.getOrNull("x"));
		env.assertAtRoot();
	}

	@Test
	void withScopeClosesOnException() {
		Env env = new Env();
		assertThrows(RuntimeException.class, () -> env.withScope("Main", () -> {
			register(env, "x");
			throw new RuntimeException("boom");
		}));
		assertNull(env.getOrNull("x"));
		env.assertAtRoot();
	}

	@Test
	void withLetFrameSharesOwnerWithParent() {
		Env env = new Env();
		env.withScope("Main", () -> env.withScope("f", () -> {
			Id ownerBefore = env.currentOwner();
			env.withLetFrame(() -> {
				assertEquals(ownerBefore, env.currentOwner());
				assertEquals("Main.f.g", register(env, "g").toString());
				return null;
			});
			assertNull(env.getOrNull("g"));
			assertEquals(ownerBefore, env.currentOwner());
			return null;
		}));
		env.assertAtRoot();
	}

	@Test
	void withLetFrameClosesOnException() {
		Env env = new Env();
		env.withScope("Main", () -> env.withScope("f", () -> {
			assertThrows(RuntimeException.class, () -> env.withLetFrame(() -> {
				register(env, "g");
				throw new RuntimeException("boom");
			}));
			assertNull(env.getOrNull("g"));
			return null;
		}));
		env.assertAtRoot();
	}

	@Test
	void withLambdaScopeProducesDistinctOwner() {
		Env env = new Env();
		env.withScope("Main", () -> env.withScope("f", () -> {
			Id lambda1 = env.withLambdaScope(env::currentOwner);
			Id lambda2 = env.withLambdaScope(env::currentOwner);

			assertTrue(lambda1.toString().startsWith("Main.f._lambda"));
			assertTrue(lambda2.toString().startsWith("Main.f._lambda"));
			assertTrue(!lambda1.equals(lambda2));
			return null;
		}));
		env.assertAtRoot();
	}

	@Test
	void nestedLetFramesDoNotLeak() {
		Env env = new Env();
		env.withScope("Main", () -> env.withScope("f", () -> {
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
		env.assertAtRoot();
	}

	@Test
	void sameOwnerDoesNotReuseNameAfterLetFrameCloses() {
		Env env = new Env();
		env.withScope("Main", () -> env.withScope("f", () -> {
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
		env.withScope("Main", () -> {
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
		env.withScope("Main", () -> {
			Id owner = env.currentOwner();
			env.withScope("f", () -> {
				assertEquals(Id.intern("Main.f"), env.currentOwner());
				return null;
			});
			assertEquals(owner, env.currentOwner());
			return null;
		});
		env.assertAtRoot();
	}

	@Test
	void assertAtRootThrowsInsideScope() {
		Env env = new Env();
		env.withScope("Main", () -> {
			assertThrows(AssertionError.class, env::assertAtRoot);
			return null;
		});
		assertDoesNotThrow(env::assertAtRoot);
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
