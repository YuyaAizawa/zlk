package zlk.phase.anfconv;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import zlk.common.Ctor;
import zlk.common.Location;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.ir.anf.AnfAtom;
import zlk.ir.anf.AnfBind;
import zlk.ir.anf.AnfBlock;
import zlk.ir.anf.AnfBranch;
import zlk.ir.anf.AnfFunDecl;
import zlk.ir.anf.AnfModule;
import zlk.ir.anf.AnfPattern;
import zlk.ir.anf.AnfRhs;
import zlk.ir.anf.AnfTypeDecl;
import zlk.ir.anf.AnfVar;
import zlk.ir.anf.LocalId;
import zlk.ir.clcalc.CcCaseBranch;
import zlk.ir.clcalc.CcExp;
import zlk.ir.clcalc.CcFunDecl;
import zlk.ir.clcalc.CcModule;
import zlk.ir.clcalc.CcPattern;
import zlk.ir.clcalc.CcTypeDecl;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

public final class AnfConverter {
	private final CcModule src;

	public AnfConverter(CcModule src) {
		this.src = src;
	}

	public AnfModule convert() {
		return new AnfModule(
				src.name(),
				src.types().map(this::convert),
				src.funcs().map(decl -> new FunctionConverter().convert(decl)));
	}

	private AnfTypeDecl convert(CcTypeDecl decl) {
		return new AnfTypeDecl(
				decl.id(),
				decl.ctors().map(ctor -> new Ctor(ctor.id(), ctor.args(), ctor.loc())),
				decl.loc());
	}

	final class FunctionConverter {
		private AtomicInteger localIdCounter = new AtomicInteger();
		private final IdMap<AnfVar> vars = new IdMap<>();

		AnfFunDecl convert(CcFunDecl decl) {
			Seq<AnfPattern> args = decl.args().map(this::convert);
			BlockConverter bodyConverter = new BlockConverter();
			AnfAtom result = bodyConverter.convert(decl.body());
			return new AnfFunDecl(
					decl.id(),
					args,
					new AnfBlock(bodyConverter.body.toSeq(), result),
					decl.loc());
		}

		private AnfVar var(Id id, Type type) {
			AnfVar var = new AnfVar(
					new LocalId(localIdCounter.getAndIncrement()),
					Optional.of(id),
					type);
			vars.put(id, var);
			return var;
		}
		private AnfVar var(Type type) {
			return new AnfVar(
					new LocalId(localIdCounter.getAndIncrement()),
					Optional.empty(),
					type);
		}

		private AnfBlock convertNestedBlock(CcExp exp) {
			BlockConverter converter = new BlockConverter();
			AnfAtom result = converter.convert(exp);
			return new AnfBlock(converter.body.toSeq(), result);
		}

		private AnfPattern convert(CcPattern pattern) {
			return switch(pattern) {
			case CcPattern.Wildcard(Type type, Location loc) ->
					new AnfPattern.Wildcard(type, loc);
			case CcPattern.Var(Id id, Type type, Location loc) ->
					new AnfPattern.Var(var(id, type), loc);
			case CcPattern.Ctor(var ctor, Seq<CcPattern> args, Type type, Location loc) ->
					new AnfPattern.Ctor(ctor.id(), args.map(this::convert), type, loc);
			case CcPattern.Record(Seq<CcPattern.Var> fields, Type type, Location loc) ->
					new AnfPattern.Record(
							fields.map(field -> new AnfPattern.Var(
									var(field.id(), field.type()), field.loc())),
							type,
							loc);
			};
		}

		private AnfBranch convert(CcCaseBranch branch) {
			AnfPattern pattern = convert(branch.pattern());
			BlockConverter converter = new BlockConverter();
			AnfAtom result = converter.convert(branch.body());
			return new AnfBranch(
					pattern,
					new AnfBlock(converter.body.toSeq(), result),
					branch.loc());
		}

		final class BlockConverter {
			SeqBuffer<AnfBind> body = new SeqBuffer<AnfBind>();

			AnfAtom convert(CcExp exp) {
				return switch(exp) {
				case CcExp.CcCnst(var value, var _) -> new AnfAtom.Cnst(value);
				case CcExp.CcVar(Id id, Type _, Location _) -> vars.get(id);
				case CcExp.CcDirectApp(Id funId, Seq<CcExp> args, Type type, Location loc) ->
						bind(new AnfRhs.DirectApp(funId, args.map(this::convert)), type, loc);
				case CcExp.CcClosureApp(CcExp fun, Seq<CcExp> args, Type type, Location loc) -> {
					AnfAtom closure = convert(fun);
					yield bind(new AnfRhs.ClosureApp(closure, args.map(this::convert)), type, loc);
				}
				case CcExp.CcMkCls(Id impl, Seq<CcExp> captures, Type type, Location loc) ->
						bind(new AnfRhs.MakeClosure(impl, captures.map(this::convert)), type, loc);
				case CcExp.CcIf(CcExp condition, CcExp thenExp, CcExp elseExp, Type type, Location loc) ->
						bind(
								new AnfRhs.If(
										convert(condition),
										convertNestedBlock(thenExp),
										convertNestedBlock(elseExp)),
								type,
								loc);
				case CcExp.CcCase(CcExp targetExp, Seq<CcCaseBranch> branches, Type type, Location loc) -> {
					AnfAtom targetAtom = convert(targetExp);
					yield bind(
							new AnfRhs.Case(
									targetAtom,
									branches.map(FunctionConverter.this::convert)),
							type,
							loc);
				}
				case CcExp.CcLet(Id id, CcExp boundExp, CcExp bodyExp, Type _, Location loc) -> {
					AnfAtom bound = convert(boundExp);
					AnfVar named = var(id, boundExp.type());
					body.add(new AnfBind(named, new AnfRhs.Move(bound), loc));
					yield convert(bodyExp);
				}
				case CcExp.CcRecord(Seq<CcExp.CcRecordField> fields, Type type, Location loc) -> {
					Seq<AnfRhs.AnfRecordField> convertedFields = fields.map(field ->
							new AnfRhs.AnfRecordField(field.name(), convert(field.value())));
					yield bind(new AnfRhs.MakeRecord(convertedFields), type, loc);
				}
				case CcExp.CcRecordAccess(CcExp record, String field, Type type, Location loc) -> {
					AnfAtom target = convert(record);
					yield bind(new AnfRhs.RecordGet(target, field), type, loc);
				}
				case CcExp.CcRecordUpdate(
						CcExp record,
						Seq<CcExp.CcRecordField> fields,
						Type type,
						Location loc
				) -> {
					AnfAtom target = convert(record);
					Seq<AnfRhs.AnfRecordField> convertedFields = fields.map(field ->
							new AnfRhs.AnfRecordField(
									field.name(),
									convert(field.value())));
					yield bind(new AnfRhs.RecordUpdate(target, convertedFields), type, loc);
				}
				};
			}

			private AnfVar bind(AnfRhs rhs, Type type, Location loc) {
				AnfVar dst = var(type);
				body.add(new AnfBind(dst, rhs, loc));
				return dst;
			}

		}
	}
}

