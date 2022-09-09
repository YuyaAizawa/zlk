package zlk.clcalc;

import zlk.common.Id;
import zlk.common.IdList;
import zlk.util.pp.PrettyPrinter;

public record CcMkCls(
		Id clsFunc, // メソッド定義
		IdList caps) // キャプチャする変数
implements CcExp {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("mkCls:").endl().inc();
		pp.field("clsFunc", clsFunc);
		pp.field("caps", caps);
		pp.dec();
	}
}
