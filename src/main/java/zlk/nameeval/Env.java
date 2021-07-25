package zlk.nameeval;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import org.objectweb.asm.MethodVisitor;

import zlk.common.Type;
import zlk.idcalc.IdArg;
import zlk.idcalc.IdBuiltin;
import zlk.idcalc.IdFun;
import zlk.idcalc.IdInfo;

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

	public IdFun registerFun(String module, String name, Type ty) {
		IdFun idFun = new IdFun(fresh++, module, name, ty);
		put(name, idFun);
		return idFun;
	}

	public IdArg registerArg(String name, Type ty, IdFun fun, int idx) {
		IdArg idArg = new IdArg(fresh++, name, ty, fun, idx);
		put(name, idArg);
		return idArg;
	}

	public IdBuiltin registerBuiltinVar(String name, Type ty, Consumer<MethodVisitor> action) {
		IdBuiltin idBuiltin = new IdBuiltin(fresh++, name, ty, action);
		put(name, idBuiltin);
		return idBuiltin;
	}
}
