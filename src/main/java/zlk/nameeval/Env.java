package zlk.nameeval;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import zlk.common.id.Id;
import zlk.util.Stack;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public final class Env {
	Stack<Scope> scopeStack;

	public Env() {
		scopeStack = new Stack<>();
		scopeStack.push(new Scope(null));
	}

	public void push(String scopeSimpleName) {
		scopeStack.push(new Scope(
				Id.fromParentAndSimpleName(scopeStack.peek().name(), scopeSimpleName)));
	}

	public void pop() {
		scopeStack.pop();
	}

	public Id getOrNull(String name) {
		for(var scope : scopeStack) {
			Id id = scope.ids().get(name);
			if(id != null) {
				return id;
			}
		}
		return null;
	}

	public Id get(String name) {
		Id id = getOrNull(name);
		if(id == null) {
			for(Scope scope: scopeStack) {
				scope.pp(System.out);
				System.out.println();
			}
			throw new NoSuchElementException(name);
		} else {
			return id;
		}
	}

	private void put(String name, Id id) {
		Id old = getOrNull(name);
		if(old != null) {
			throw new RuntimeException("already exist: "+old);
		}
		scopeStack.peek().ids().put(name, id);
	}

	public Id registerVar(String simpleName) {
		Id id = Id.fromParentAndSimpleName(scopeStack.peek().name(), simpleName);
		put(simpleName, id);
		return id;
	}

	public Id registerBuiltinVar(Id id) {
		put(id.simpleName(), id);
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
		List<? extends PrettyPrintable> entryies = ids.entrySet().stream().map(e -> new PrettyPrintable() {
			@Override
			public void mkString(PrettyPrinter pp) {
				pp.append(e.getKey()).append(": ").append(e.getValue().canonicalName());
			}
		}).toList();
		pp.append("name: ").append(name).append(", entry: [").oneline(entryies, ", ").append("]");
	}
}
