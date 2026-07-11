package zlk.nameeval;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import zlk.common.id.Id;
import zlk.util.collection.Stack;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

final class Env {
	Map<String, Id> global;
	Stack<Scope> scopes;

	public Env() {
		global = new HashMap<>();
		scopes = new Stack<>();
	}

	public void pushScope(String scopeSimpleName) {
		Id scopeName = scopes.isEmpty()
				? Id.intern(scopeSimpleName)
				: Id.intern(scopes.peek().name(), scopeSimpleName);
		scopes.push(new Scope(scopeName));
	}
	public void pushScope() { // lambda式用
		pushScope("_lambda"+(scopes.peek().lambdaCounter().getAndIncrement()));
	}

	public void popScope() {
		scopes.pop();
	}

	public Id getOrNull(String name) {
		for(var scope : scopes) {
			Id id = scope.ids().get(name);
			if(id != null) {
				return id;
			}
		}
		return global.get(name);
	}

	public Id get(String name) {
		Id id = getOrNull(name);
		if(id == null) {
			throw new NoSuchElementException(name);
		}
		return id;
	}

	public Id getScopeName() {
		return scopes.peek().name();
	}

	/**
	 * この環境に現在のスコープを基準に名前を登録し，生成した{@link Id}を返す.
	 *
	 * @param name
	 * @return 生成したID
	 * @throws DuplicatedNameException
	 */
	public Id register(String name) throws DuplicatedNameException {
		Scope topScope = scopes.peek();
		Id id = Id.intern(topScope.name(), name);
		return register(name, id);
	}

	public Id register(String name, Id id) throws DuplicatedNameException {
		Scope topScope = scopes.peek();
		Id orig = topScope.ids().putIfAbsent(name, id);

		if(orig != null) {
			throw new DuplicatedNameException(orig, id);
		}
		return id;
	}

	/**
	 * この環境の大域に指定した{@link Id}をその{@link Id#simpleName()}}で登録し，Idを返す.
	 *
	 * @param name
	 * @return 生成したID
	 * @throws DuplicatedNameException
	 */
	public Id registerGlobal(Id id) throws DuplicatedNameException {
		String name = id.simpleName();
		Id orig = global.putIfAbsent(name, id);
		if(orig != null) {
			throw new DuplicatedNameException(orig, id);
		}
		return id;
	}
}

record Scope(
		Id name,
		Map<String, Id> ids,
		AtomicInteger lambdaCounter
) implements PrettyPrintable {

	Scope(Id id) {
		this(id, new HashMap<>(), new AtomicInteger(1));
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(name).append(": ").append(PrettyPrintable.oneLine(ids));
	}
}

final class TyEnv {
	private final Map<String, Id> impl;

	TyEnv() {
		impl = new HashMap<>();
	}

	Id register(String name, Id id) throws DuplicatedNameException {
		Id old = impl.putIfAbsent(name, id);
		if(old != null) {
			throw new DuplicatedNameException(old, id);
		}
		return id;
	}

	Id get(String name) {
		Id result = impl.get(name);
		if(result == null) {
			throw new NoSuchElementException(name);
		}
		return result;
	}
}

class DuplicatedNameException extends Exception {
	private static final long serialVersionUID = 1L;

	public final Id oldId;
	public final Id newId;

	public DuplicatedNameException(Id oldId, Id newId) {
		super("old: "+oldId+", new: "+newId);
		this.oldId = oldId;
		this.newId = newId;
	}
}
