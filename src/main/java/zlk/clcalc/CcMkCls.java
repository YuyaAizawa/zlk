package zlk.clcalc;

import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.util.Location;
import zlk.util.pp.PrettyPrinter;

public record CcMkCls(
		Id clsFunc, // メソッド定義
		IdList caps,
		Location loc) // キャプチャする変数
implements CcExp {

	public Id id() {
		return clsFunc;
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("mkCls:").endl().inc();
		pp.field("clsFunc", clsFunc);
		pp.field("caps", caps);
		pp.dec();
	}
}
