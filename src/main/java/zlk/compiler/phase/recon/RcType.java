package zlk.compiler.phase.recon;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import zlk.compiler.id.Id;
import zlk.compiler.ir.typing.RecordField;
import zlk.compiler.ir.typing.Type;
import zlk.compiler.phase.recon.RcType.AppN;
import zlk.compiler.phase.recon.RcType.FunN;
import zlk.compiler.phase.recon.RcType.RecordN;
import zlk.compiler.phase.recon.RcType.VarN;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;


public sealed interface RcType extends PrettyPrintable
permits VarN, AppN, FunN, RecordN {
	record VarN(Variable var) implements RcType {}
	record AppN(Id id, Seq<RcType> args) implements RcType {}
	record FunN(RcType arg, RcType ret) implements RcType {}

	/**
	 * レコードのrow．RcTypeではない独立record．RecordNが包む．
	 *
	 * @param fields canonicalize済みのフィールド列
	 * @param extension 末尾のrow変数．空ならclosed row
	 */
	record RowN(Seq<RecordField<RcType>> fields, Optional<Variable> extension) {
		public RowN {
			fields = RecordField.canonicalize(fields);
			Objects.requireNonNull(extension);
			extension.ifPresent(var -> {
				if(var.kind() != Variable.Kind.ROW) {
					throw new IllegalArgumentException("row extension must have ROW kind");
				}
			});
		}

		/** closed rowの便利constructor． */
		public RowN(Seq<RecordField<RcType>> fields) {
			this(fields, Optional.empty());
		}

		public Seq<RecordField<RcType>> fields() { return fields; }
		public Optional<Variable> extension() { return extension; }
	}

	/**
	 * レコード型．{@link RowN}を包む．
	 */
	record RecordN(RowN row) implements RcType {
		public RecordN(Seq<RecordField<RcType>> fields) {
			this(new RowN(fields, Optional.empty()));
		}

		public RecordN(Seq<RecordField<RcType>> fields, Optional<Variable> extension) {
			this(new RowN(fields, extension));
		}

		public Seq<RecordField<RcType>> fields() { return row.fields(); }
		public Optional<Variable> extension() { return row.extension(); }
	}

	public static final RcType BOOL = new AppN(Type.BOOL.id(), Seq.of());
	public static final RcType I32  = new AppN(Type.I32.id() , Seq.of());

	/**
	 * 型注釈から生成された制約用の型情報
	 *
	 * @param rigids この注釈で新しく導入したrigidな型変数
	 * @param argTys 関数型だった場合の引数型をflattenしたもの
	 * @param resultTy 関数型だった場合の戻り値の型かその他の場合の型そのもの
	 * @param typeVars 外側から共有したものを含む型変数名と型変数の対応
	 * @param type 型注釈全体の制約用の型
	 */
	public record Anno(
			Seq<Variable> rigids,
			Seq<RcType> argTys,
			RcType resultTy,
			Map<String, Variable> typeVars,
			RcType type) {}

	/**
	 * 多相型をfresh flexでインスタンス化した結果
	 *
	 * @param flexes インスタンス化で新しく導入したflexibleな型変数
	 * @param argTys 関数型だった場合の引数型をflattenしたもの
	 * @param resultTy 関数型だった場合の戻り値の型かその他の場合の型そのもの
	 * @param type インスタンス化した型全体
	 */
	public record Inst(
			Seq<Variable> flexes,
			Seq<RcType> argTys,
			RcType resultTy,
			RcType type) {}

	/**
	 * 多相型をfresh flexでインスタンス化する．
	 *
	 * @param ty インスタンス化する型
	 * @param freshFlex fresh flexの生成器
	 * @return インスタンス化した制約用の型情報
	 */
	public static Inst instantiate(Type ty, FreshFlex freshFlex) {
		Map<String, Variable> typeVars = new HashMap<>();
		Map<String, Variable> rowVars = new HashMap<>();
		SeqBuffer<Variable> flexes = new SeqBuffer<>();
		RcType type = convert(ty, name -> typeVars.computeIfAbsent(name, _ -> {
			Variable flex = freshFlex.getVariable(name, 0, Variable.Kind.TYPE);
			flexes.add(flex);
			return flex;
		}), rowName -> rowVars.computeIfAbsent(rowName, _ -> {
			Variable flex = freshFlex.getVariable(rowName, 0, Variable.Kind.ROW);
			flexes.add(flex);
			return flex;
		}));
		return new Inst(flexes.toSeq(), argTys(type), resultTy(type), type);
	}

	/**
	 * 型注釈をrigidな制約用の型へ変換する．外側の注釈と同名の型変数は共有する．
	 *
	 * @param ty 型注釈
	 * @param outerTypeVars 外側の型注釈で導入済みの型変数
	 * @return 制約用の型情報
	 */
	public static Anno fromAnnotation(Type ty, Map<String, Variable> outerTypeVars) {
		Map<String, Variable> typeVars = new HashMap<>(outerTypeVars);
		Map<String, Variable> rowVars = new HashMap<>();
		SeqBuffer<Variable> rigids = new SeqBuffer<>();
		RcType type = convert(ty, name -> typeVars.computeIfAbsent(name, _ -> {
			Variable rigid = Variable.ofRigid(name);
			rigids.add(rigid);
			return rigid;
		}), rowName -> rowVars.computeIfAbsent(rowName, _ -> {
			Variable rigid = Variable.ofRigid(rowName, Variable.Kind.ROW);
			rigids.add(rigid);
			return rigid;
		}));
		return new Anno(
				rigids.toSeq(),
				argTys(type),
				resultTy(type),
				Map.copyOf(typeVars),
				type);
	}

	/**
	 * TypeからRcTypeへのkind-aware変換．
	 * Type.VarはTYPE-kind，Type.RowVarはROW-kindのVariableへ変換する．
	 * 同名をType.VarとRowVarの両方に使うとrejectする．
	 * Type.Rowのextensionをsilentに捨てない．
	 */
	private static RcType convert(
			Type ty,
			Function<String, Variable> typeVar,
			Function<String, Variable> rowVar) {
		// 同名のType.VarとType.RowVarが混在するかの検査用
		Map<String, Variable> seenType = new HashMap<>();
		Map<String, Variable> seenRow = new HashMap<>();

		Function<Type, RcType> conv = new Function<>() {
			@Override
			public RcType apply(Type t) {
				return switch(t) {
				case Type.Var(String name) -> {
					// 同名row変数が既に存在したらreject
					if(seenRow.containsKey(name)) {
						throw new IllegalArgumentException(
								"name '" + name + "' used as both type variable and row variable");
					}
					Variable v = typeVar.apply(name);
					seenType.put(name, v);
					yield new VarN(v);
				}
				case Type.CtorApp(Id id, Seq<Type> args) ->
					new AppN(id, args.map(this));
				case Type.Arrow(Type arg, Type ret) ->
					new FunN(apply(arg), apply(ret));
				case Type.Record(Type.Row row) -> {
					// rowのextensionを保持する
					Optional<Variable> ext = row.extension().map(rv -> {
						// 同名type変数が既に存在したらreject
						if(seenType.containsKey(rv.name())) {
							throw new IllegalArgumentException(
									"name '" + rv.name() + "' used as both type variable and row variable");
						}
						Variable rowVariable = rowVar.apply(rv.name());
						seenRow.put(rv.name(), rowVariable);
						return rowVariable;
					});
					Seq<RecordField<RcType>> fields = row.fields().map(field ->
							new RecordField<>(field.name(), apply(field.value())));
					yield new RecordN(new RowN(fields, ext));
				}
				};
			}
		};

		return conv.apply(ty);
	}

	private static Seq<RcType> argTys(RcType type) {
		SeqBuffer<RcType> argTys = new SeqBuffer<>();
		RcType resultTy = type;
		while (resultTy instanceof RcType.FunN(RcType arg, RcType ret)) {
			argTys.add(arg);
			resultTy = ret;
		}
		return argTys.toSeq();
	}

	private static RcType resultTy(RcType type) {
		RcType resultTy = type;
		while (resultTy instanceof RcType.FunN(_, RcType ret)) {
			resultTy = ret;
		}
		return resultTy;
	}

	default Type toType() {
		return switch(this) {
		case VarN(Variable var) -> var.toType();
		case AppN(Id id, Seq<RcType> args) ->
			new Type.CtorApp(id, args.map(arg -> arg.toType()));
		case FunN(RcType arg, RcType ret) ->
			Type.arrow(Seq.of(arg.toType(), ret.toType()));
		case RecordN(RowN row) -> {
			// rowのextensionをType.Rowへ変換
			Optional<Type.RowVar> ext = row.extension().map(v -> {
				Optional<String> name = v.getPreferredName();
				return new Type.RowVar(name.orElse("row"));
			});
			yield new Type.Record(new Type.Row(
				row.fields().map(field ->
					new RecordField<>(field.name(), field.value().toType())),
				ext));
		}
		};
	}

	@Override
	default void mkString(PrettyPrinter pp) {
		switch(this) {
		case VarN(Variable var) -> {
			pp.append(var);
		}
		case AppN(Id id, Seq<RcType> args) -> {
			pp.append(id);
			for(RcType arg : args) {
				pp.append(" ");
				switch(arg) {
				case VarN _ ->
					pp.append(arg);
				case AppN(Id _, Seq<RcType> args_) when args_.isEmpty() ->
					pp.append(arg);
				default ->
					pp.append("(").append(arg).append(")");
				}
			}
		}
		case FunN(RcType arg, RcType ret) -> {
			switch(arg) {
			case FunN _ -> pp.append("(").append(arg).append(")");
			default -> pp.append(arg);
			}
			pp.append(" -> ");
			pp.append(ret);
		}
		case RecordN(RowN row) -> {
			pp.append("{ ");
			if(row.extension().isPresent()) {
				pp.append(row.extension().get()).append(" | ");
			}
			Seq<RecordField<RcType>> fields = row.fields();
			fields.forEachIndexed((i, field) -> {
				pp.append(i == 0 ? "" : ", ");
				pp.append(field.name()).append(" : ").append(field.value());
			});
			if(!fields.isEmpty()) pp.append(" ");
			pp.append("}");
		}
		}
	}
}