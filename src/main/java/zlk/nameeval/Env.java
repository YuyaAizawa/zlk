package zlk.nameeval;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import zlk.common.id.Id;
import zlk.util.collection.Stack;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public final class Env {
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
		if(name.contains(".")) {
			throw new Error("name cannot contain '.': "+name);
		}

		Scope topScope = scopes.peek();
		Id id = Id.intern(topScope.name(), name);
		Id orig = topScope.ids().put(name, id);

		if(orig != null) {
			throw new DuplicatedNameException(topScope.name(), name);
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
		Id orig = global.put(name, id);
		if(orig != null) {
			throw new DuplicatedNameException(null, name);
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

class DuplicatedNameException extends Exception {
	private static final long serialVersionUID = 1L;

	public final Id scopeId;
	public final String name;

	public DuplicatedNameException(Id scopeId, String name) {
		super("scope: "+scopeId+", name: "+name);
		this.scopeId = scopeId;
		this.name = name;
	}
}