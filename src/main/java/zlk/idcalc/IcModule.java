package zlk.idcalc;

import java.util.List;

import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 名前解決したモジュール
 * @param name モジュール名
 * @param types 型の定義
 * @param decls トップレベルの関数の定義
 * @param recscc 関数の含まれる強連結成分
 * @param origin ソースコード名
 */
public record IcModule(
		String name,
		List<IcTypeDecl> types,
		List<IcValDecl> decls,
		IdMap<IdList> recscc,
		String origin
) implements PrettyPrintable {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append("module ").append(name()).endl();

		types.forEach(ty -> {
			pp.endl();
			pp.append(ty).endl();
		});

		decls.forEach(decl -> {
			pp.endl();
			pp.append(decl).endl();
		});

		recscc.forEach((k,v) ->{
			pp.endl();
			pp.append(k).append(": ").append(v);
		});
	}
}
