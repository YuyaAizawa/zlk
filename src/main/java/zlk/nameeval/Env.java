package zlk.nameeval;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import org.objectweb.asm.MethodVisitor;

import zlk.common.Type;
import zlk.idcalc.IdInfo;
import zlk.idcalc.InfoArg;
import zlk.idcalc.InfoBuiltin;
import zlk.idcalc.InfoFun;

public final class Env {
	int fresh = 0;
	Deque<Map<String, IdInfo>> envStack = new ArrayDeque<>();

	public Env() {
		push();
	}

	public void push() {
		envStack.push(new HashMap<>());
	}

	public void pop() {
		envStack.pop();
	}

	public IdInfo get(String name) {
		IdInfo info = getOrNull(name);
		if(info == null) {
			throw new NoSuchElementException(name);
		} else {
			return info;
		}
	}

	public IdInfo getOrNull(String name) {
		for(var env : envStack) {
			IdInfo value = env.get(name);
			if(value != null) {
				return value;
			}
		}
		return null;
	}

	private void put(String name, IdInfo info) {
		IdInfo old = getOrNull(name);
		if(old != null) {
			throw new IllegalArgumentException("already exist: "+old);
		}
		envStack.peek().put(name, info);
	}

	public IdInfo registerFun(String module, String name, Type ty) {
		IdInfo idFun = new IdInfo(fresh++, new InfoFun(module, name, ty));
		put(name, idFun);
		return idFun;
	}

	public IdInfo registerArg(String name, Type ty, InfoFun fun, int idx) {
		IdInfo idArg = new IdInfo(fresh++, new InfoArg(name, ty, fun, idx));
		put(name, idArg);
		return idArg;
	}

	public IdInfo registerBuiltinVar(String name, Type ty, Consumer<MethodVisitor> action) {
		IdInfo idBuiltin = new IdInfo(fresh++, new InfoBuiltin(name, ty, action));
		put(name, idBuiltin);
		return idBuiltin;
	}
}
