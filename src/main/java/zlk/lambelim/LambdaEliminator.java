package zlk.lambelim;

import java.util.List;

import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.common.type.Type;
import zlk.idcalc.IcApp;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcIf;
import zlk.idcalc.IcLet;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcVar;
import zlk.util.Location;

public final class LambdaEliminator {
	private final IdMap<Type> type;

	public LambdaEliminator(IdMap<Type> type) {
		this.type = type;
	}

	public IcModule compile(IcModule module) {
		List<IcDecl> eliminated =
				module.decls().stream()
						.map(this::compile)
						.toList();

		return new IcModule(module.name(), eliminated, module.origin());
	}

	private IcDecl compile(IcDecl decl) {
		return new IcDecl(decl.id(), decl.args(), decl.type(), compile(decl.body()), decl.loc());
	}

	private IcExp compile(IcExp exp) {
		return exp.fold(
				cnst -> cnst,
				var  -> var,
				app  -> new IcApp(
						compile(app.fun()),
						app.args().stream().map(this::compile).toList(),
						app.loc()),
				if_  -> new IcIf(
						compile(if_.cond()),
						compile(if_.exp1()),
						compile(if_.exp2()),
						if_.loc()),
				let  -> new IcLet(compile(let.decl()), compile(let.body()), let.loc()),
				lamb -> {
					Id id = lamb.lambId();
					IdList args = IdList.of(lamb.varId());
					Location noLoc = Location.noLocation();
					return new IcLet(
							new IcDecl(id, args, type.get(id), compile(lamb.body()), noLoc),
							new IcVar(id, noLoc),
							lamb.loc());
				});
	}
}
