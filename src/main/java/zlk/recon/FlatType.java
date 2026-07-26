package zlk.recon;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import zlk.common.RecordField;
import zlk.common.id.Id;
import zlk.recon.FlatType.CtorApp1;
import zlk.recon.FlatType.Fun1;
import zlk.recon.FlatType.Record1;
import zlk.recon.FlatType.Row1;
import zlk.recon.constraint.Content;
import zlk.util.collection.Seq;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 単一化後の型
 *
 * <ul>
 *   <li> {@link CtorApp1} -- 関数型以外の型
 *   <li> {@link Fun1} -- 関数型
 *   <li> {@link Record1} -- レコード型（外側TYPE-kind rootのStructureに入る）
 *   <li> {@link Row1} -- レコードのrow（内側ROW-kind rootのStructureに入る）
 * </ul>
 */
public sealed interface FlatType extends PrettyPrintable
permits CtorApp1, Fun1, Record1, Row1 {
	record CtorApp1(Id id, Seq<Variable> args) implements FlatType {} // TODO: App1?
	record Fun1(Variable arg, Variable ret) implements FlatType {}

	/**
	 * レコードのrow．ROW-kind rootのStructureに入る．
	 *
	 * @param fields canonicalize済みのフィールド列
	 * @param extension 末尾のrow変数．空ならclosed row
	 */
	record Row1(Seq<RecordField<Variable>> fields, Optional<Variable> extension) implements FlatType {
		public Row1 {
			fields = RecordField.canonicalize(fields);
			Objects.requireNonNull(extension);
		}

		/** closed rowの便利constructor． */
		public Row1(Seq<RecordField<Variable>> fields) {
			this(fields, Optional.empty());
		}

		public boolean isOpen() {
			return extension.isPresent();
		}
	}

	/**
	 * レコード型．TYPE-kind rootのStructureに入る．内包するrowを1つのVariableで参照する．
	 *
	 * @param row 内包するrowを示すROW-kind変数
	 */
	record Record1(Variable row) implements FlatType {}

	default FlatType traverse(Function<Variable, Variable> f) {
		return switch(this) {
		case CtorApp1(Id id, Seq<Variable> args) -> new CtorApp1(id, args.map(f));
		case Fun1(Variable arg, Variable ret) -> new Fun1(f.apply(arg), f.apply(ret));
		case Record1(Variable row) -> new Record1(f.apply(row));
		case Row1(Seq<RecordField<Variable>> fields, Optional<Variable> extension) ->
			new Row1(
					fields.map(field -> new RecordField<>(field.name(), f.apply(field.value()))),
					extension.map(f));
		};
	}

	/**
	 * ROW-kind rootのVariableからType.Rowへの変換は{@link Variable#toRow}へ委譲する．
	 * FlatType自身の重複toType()/rowToType()はVariable.toType(IntFunction)へ一本化済み．
	 */

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case CtorApp1(Id id, Seq<Variable> args) -> {
			pp.append(id);
			for(Variable arg : args) {
				pp.append(" ");
				if(arg.get().content instanceof Content.Structure structure
						&& structure.flatType() instanceof CtorApp1 ctorApp
						&& ctorApp.args.size() > 0) {
					// 複数トークンはカッコが要る
					pp.append("(").append(arg).append(")");
				} else {
					pp.append(arg);
				}
			}
		}
		case Fun1(Variable arg, Variable ret) -> {
			pp.append(arg).append(" -> ").append(ret);
		}
		case Record1(Variable row) -> {
			VariableState rowState = row.get();
			if(rowState.content instanceof Content.Structure structure
					&& structure.flatType() instanceof Row1 row1) {
				RecordField.appendTo(pp, row1.fields(), " : ");
			} else {
				pp.append("Record(").append(row).append(")");
			}
		}
		case Row1(Seq<RecordField<Variable>> fields, Optional<Variable> extension) -> {
			pp.append("{ ");
			if(extension.isPresent()) {
				pp.append(extension.get()).append(" | ");
			}
			for(int i = 0; i < fields.size(); i++) {
				if(i > 0) pp.append(", ");
				RecordField<Variable> field = fields.at(i);
				pp.append(field.name()).append(" : ").append(field.value());
			}
			pp.append(" }");
		}
		}
	}
}