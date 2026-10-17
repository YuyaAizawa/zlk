package zlk.compiler.ir.typing;

import zlk.compiler.id.Id;
import zlk.util.collection.Seq;

/**
 * 型の文字列化に使う透明なalias宣言．意味型や実行時の値には保持しない．
 * @param id aliasの識別子
 * @param parameters 宣言順のparameterとkind
 * @param template 透明展開済みの型
 */
public record TypeAlias(Id id, Seq<Parameter> parameters, Type template) {
	public enum Kind { TYPE, ROW }
	public record Parameter(String name, Kind kind) {}
}
