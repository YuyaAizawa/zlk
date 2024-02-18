package zlk.clcalc;

import java.util.HashSet;
import java.util.Set;

import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.idcalc.IcPattern;
import zlk.util.Location;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record CcCaseBranch(
		IcPattern pattern,
		CcExp body,
		Location loc)
implements PrettyPrintable {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(pattern).append(" ->").inc().endl();
		pp.append(body).dec();
	}

	CcCaseBranch substId(IdMap<Id> map) {
		Set<Id> ids = new HashSet<>();
		pattern.addVars(ids);
		ids.forEach(id -> {
			if(map.containsKey(id)) {
				throw new RuntimeException(""+id);
			}
		});
		return new CcCaseBranch(pattern, body.substId(map), loc);
	}
}