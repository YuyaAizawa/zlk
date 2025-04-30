package zlk.nameeval;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import zlk.common.id.Id;
import zlk.util.Stack;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public final class Env {
	Map<String, Id> global;
	Stack<Scope> scoped;

	public Env() {
		global = new HashMap<>();
		scoped = new Stack<>();
	}

	public void pushScope(String scopeSimpleName) {
		Id scopeName = scoped.isEmpty()
				? Id.fromCanonicalName(scopeSimpleName)
				: Id.fromParentAndSimpleName(scoped.peek().name(), scopeSimpleName);
		scoped.push(new Scope(scopeName));
	}

	public void popScope() {
		scoped.pop();
	}

	public Id getOrNull(String name) {
		for(var scope : scoped) {
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

		Scope topScope = scoped.peek();
		Id id = Id.fromParentAndSimpleName(topScope.name(), name);
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
		Map<String, Id> ids
) implements PrettyPrintable {

	Scope(Id id) {
		this(id, new HashMap<>());
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(name).append(": ").append(PrettyPrintable.from(
				ids,
				s -> pp_ -> pp_.append(s),
				i -> i
		));
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