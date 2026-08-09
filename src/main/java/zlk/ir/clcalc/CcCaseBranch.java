package zlk.ir.clcalc;

import zlk.common.Location;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.util.collection.SeqBuffer;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public record CcCaseBranch(
		CcPattern pattern,
		CcExp body,
		Location loc)
implements PrettyPrintable {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(pattern).append(" ->").inc().endl();
		pp.indent(() -> {
			pp.append(body);
		});
	}

	CcCaseBranch substId(IdMap<Id> map) {
		SeqBuffer<Id> ids = new SeqBuffer<Id>();
		pattern.walkVars(ids::add);
		ids.forEach(id -> {
			if(map.containsKey(id)) {
				throw new RuntimeException(""+id);
			}
		});
		return new CcCaseBranch(pattern, body.substId(map), loc);
	}
}