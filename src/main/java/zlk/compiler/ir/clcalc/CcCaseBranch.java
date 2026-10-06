package zlk.compiler.ir.clcalc;

import zlk.compiler.id.Id;
import zlk.compiler.id.IdMap;
import zlk.compiler.source.Location;
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