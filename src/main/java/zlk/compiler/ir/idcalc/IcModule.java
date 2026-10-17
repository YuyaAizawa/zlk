package zlk.compiler.ir.idcalc;

import zlk.compiler.ir.typing.TypeAlias;
import zlk.compiler.ir.typing.TypeDecl;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 名前解決したモジュール
 * @param name モジュール名
 * @param types 型の定義
 * @param decls トップレベルの関数の定義
 * @param typeAliases 型の文字列化にだけ使用する宣言順のalias候補
 */
public record IcModule(
		String name,
		Seq<TypeDecl> types,
		Seq<TypeAlias> typeAliases,
		Seq<IcValDecl> decls
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
	}
}
