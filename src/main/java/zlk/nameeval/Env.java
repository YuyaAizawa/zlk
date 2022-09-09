package zlk.nameeval;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import org.objectweb.asm.MethodVisitor;

import zlk.common.IdGenerator;
import zlk.common.Id;
import zlk.common.Type;

public final class Env {
	IdGenerator fresh;
	Deque<Map<String, Id>> envStack = new ArrayDeque<>();

	public Env(IdGenerator fresh) {
		this.fresh = fresh;
		push();
	}

	public void push() {
		envStack.push(new HashMap<>());
	}

	public void pop() {
		envStack.pop();
	}

	public Id get(String name) {
		Id info = getOrNull(name);
		if(info == null) {
			throw new NoSuchElementException(name);
		} else {
			return info;
		}
	}

	public Id getOrNull(String name) {
		for(var env : envStack) {
			Id value = env.get(name);
			if(value != null) {
				return value;
			}
		}
		return null;
	}

	private void put(String name, Id info) {
		Id old = getOrNull(name);
		if(old != null) {
			throw new IllegalArgumentException("already exist: "+old);
		}
		envStack.peek().put(name, info);
	}

	public Id registerVar(String name, Type ty) {
		Id idFun = fresh.generate(name, ty);
		put(name, idFun);
		return idFun;
	}

	public Id registerArg(Id fun, int index, String name, Type ty) {
		Id idArg = fresh.generate(name, ty);
		put(name, idArg);
		return idArg;
	}

	public Id registerBuiltinVar(String name, Type ty, Consumer<MethodVisitor> action) {
		Id idBuiltin = fresh.generate(name, ty);
		put(name, idBuiltin);
		return idBuiltin;
	}
}
