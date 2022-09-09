package zlk.clcalc;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import zlk.common.Id;
import zlk.common.IdList;
import zlk.util.pp.PrettyPrintable;

public sealed interface CcExp extends PrettyPrintable
permits CcConst, CcVar, CcCall, CcMkCls, CcIf, CcLet {

	default <R> R fold(
			Function<CcConst, R> forConst,
			Function<CcVar, R> forVar,
			Function<CcCall, R> forCall,
			Function<CcMkCls, R> forMkCls,
			Function<CcIf, R> forIf,
			Function<CcLet, R> forLet) {
		if(this instanceof CcConst cnst) {
			return forConst.apply(cnst);
		} else if(this instanceof CcVar var) {
			return forVar.apply(var);
		} else if(this instanceof CcCall call) {
			return forCall.apply(call);
		} else if(this instanceof CcMkCls mkCls) {
			return forMkCls.apply(mkCls);
		} else if(this instanceof CcIf ifExp) {
			return forIf.apply(ifExp);
		} else if(this instanceof CcLet let) {
			return forLet.apply(let);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	default void match(
			Consumer<CcConst> forConst,
			Consumer<CcVar> forVar,
			Consumer<CcCall> forCall,
			Consumer<CcMkCls> forMkCls,
			Consumer<CcIf> forIf,
			Consumer<CcLet> forLet) {
		if(this instanceof CcConst cnst) {
			forConst.accept(cnst);
		} else if(this instanceof CcVar var) {
			forVar.accept(var);
		} else if(this instanceof CcCall call) {
			forCall.accept(call);
		} else if(this instanceof CcMkCls mkCls) {
			forMkCls.accept(mkCls);
		} else if(this instanceof CcIf ifExp) {
			forIf.accept(ifExp);
		} else if(this instanceof CcLet let) {
			forLet.accept(let);
		} else {
			throw new Error(this.getClass().toString());
		}
	}

	default CcExp subst(Map<Id, CcExp> map) {
		return fold(
				cnst -> cnst,
				var -> map.getOrDefault(var.id(), var),
				call -> new CcCall(
						call.fun().subst(map),
						call.args().stream().map(arg -> arg.subst(map)).toList()),
				mkCls -> {
					if(map.containsKey(mkCls.clsFunc())) {
						// TODO クロージャが変更される場合はletにして置き換える
						throw new AssertionError("not supported dynamic closuers");
					}
					return new CcMkCls(
							mkCls.clsFunc(),

							// TODO 必要があればletを足す
							new IdList(mkCls.caps().stream().map(forceVar(map)).toList()));
				},
				if_ -> new CcIf(
						if_.cond().subst(map),
						if_.thenExp().subst(map),
						if_.elseExp().subst(map)),
				let -> new CcLet(
						let.boundVar(),
						let.boundExp().subst(map),
						let.mainExp().subst(map),
						let.varType()));
	}

	static Function<Id, Id> forceVar(Map<Id, CcExp> map) {
		return id -> {
			CcExp exp = map.get(id);
			if(exp == null) {
				return id;
			}
			if(exp instanceof CcVar var) {
				return var.id();
			}
			throw new AssertionError();
		};
	}

	static boolean isIf(CcExp exp) {
		return exp instanceof CcIf;
	}

	static boolean isLet(CcExp exp) {
		return exp instanceof CcLet;
	}
}
