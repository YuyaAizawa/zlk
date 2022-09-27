package zlk.clcalc;

import java.util.function.Consumer;
import java.util.function.Function;

import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.util.Location;
import zlk.util.pp.PrettyPrintable;

public sealed interface CcExp extends PrettyPrintable
permits CcConst, CcVar, CcCall, CcMkCls, CcIf, CcLet {

	Location loc();

	default <R> R fold(
			Function<CcConst, ? extends R> forConst,
			Function<CcVar, ? extends R> forVar,
			Function<CcCall, ? extends R> forCall,
			Function<CcMkCls, ? extends R> forMkCls,
			Function<CcIf, ? extends R> forIf,
			Function<CcLet, ? extends R> forLet) {
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

	default CcExp substId(IdMap<Id> map) {
		return fold(
				cnst  -> cnst,
				var   -> new CcVar(map.getOrDefault(var.id(), var.id()), var.loc()),
				call  -> new CcCall(
						call.fun().substId(map),
						call.args().stream().map(arg -> arg.substId(map)).toList(),
						call.loc()),
				mkCls -> new CcMkCls(
						map.getOrDefault(mkCls.clsFunc(), mkCls.clsFunc()),
						mkCls.caps().substId(map),
						mkCls.loc()),
				if_   -> new CcIf(
						if_.cond().substId(map),
						if_.thenExp().substId(map),
						if_.elseExp().substId(map),
						if_.loc()),
				let   -> new CcLet(
						let.boundVar(),
						let.boundExp().substId(map),
						let.mainExp().substId(map),
						let.loc()));
	}

	static Function<Id, Id> forceVar(IdMap<CcExp> map) {
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
