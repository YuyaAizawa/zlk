package zlk.restoreargs;

import java.util.List;

import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.common.type.Type;
import zlk.idcalc.IcApp;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcVar;
import zlk.util.Location;

public final class ArgumentsRestorer {
	private final IdMap<Type> type;

	public ArgumentsRestorer(IdMap<Type> type) {
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
		int additionalArgs = type.get(decl.id()).flatten().size()-1 - decl.args().size();
		if(additionalArgs == 0) {
			return decl;
		}
		System.out.println(decl.id() + " is restored");
		IdList additionalIds = new IdList();
		for(int i = 0;i < additionalArgs;i++) {
			additionalIds.add(decl.id().child("0"+i));
		}
		IdList declArgs = new IdList(decl.args());
		declArgs.addAll(additionalIds);
		List<IcExp> bodyArgs = additionalIds.stream().map(id -> (IcExp)new IcVar(id, Location.noLocation())).toList();

		return new IcDecl(
				decl.id(),
				declArgs,
				decl.type(),
				new IcApp(decl.body(), bodyArgs, Location.noLocation()),
				decl.loc());
	}
}
