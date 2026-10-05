package zlk.compiler.phase.reuse;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import zlk.compiler.id.Id;
import zlk.compiler.id.IdMap;
import zlk.compiler.ir.ConstValue;
import zlk.compiler.ir.clcalc.CcCaseBranch;
import zlk.compiler.ir.clcalc.CcExp;
import zlk.compiler.ir.clcalc.CcFunDecl;
import zlk.compiler.ir.clcalc.CcModule;
import zlk.compiler.ir.clcalc.CcPattern;
import zlk.compiler.ir.reuse.LocalVar;
import zlk.compiler.ir.reuse.anf.AnfBind;
import zlk.compiler.ir.reuse.anf.AnfBlock;
import zlk.compiler.ir.reuse.anf.AnfBranch;
import zlk.compiler.ir.reuse.anf.AnfFunDecl;
import zlk.compiler.ir.reuse.anf.AnfModule;
import zlk.compiler.ir.reuse.anf.AnfPattern;
import zlk.compiler.ir.reuse.anf.AnfRhs;
import zlk.compiler.ir.typing.Type;
import zlk.compiler.source.Location;
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;

public final class AnfConverter {

	public static AnfModule convert(CcModule src) {
		return new AnfModule(
				src.name(),
				src.types(),
				src.funcs().map(decl -> new FunctionConverter().convert(decl)));
	}
}

final class FunctionConverter {
	private AtomicInteger localIdCounter = new AtomicInteger();
	private final IdMap<LocalVar> vars = new IdMap<>();

	AnfFunDecl convert(CcFunDecl decl) {
		Seq<AnfPattern> args = decl.args().map(this::convert);
		BlockConverter bodyConverter = new BlockConverter();
		LocalVar result = bodyConverter.convert(decl.body());
		return new AnfFunDecl(
				decl.id(),
				args,
				new AnfBlock(bodyConverter.body.toSeq(), result, decl.loc()),
				localIdCounter.get(),
				decl.loc());
	}

	private LocalVar var(Id id, Type type) {
		LocalVar var = new LocalVar(
				localIdCounter.getAndIncrement(),
				Optional.of(id),
				type);
		vars.put(id, var);
		return var;
	}

	private AnfBlock convertNestedBlock(CcExp exp) {
		BlockConverter converter = new BlockConverter();
		LocalVar result = converter.convert(exp);
		return new AnfBlock(converter.body.toSeq(), result, exp.loc());
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
		LocalVar result = converter.convert(branch.body());
		return new AnfBranch(
				pattern,
				new AnfBlock(converter.body.toSeq(), result, branch.loc()),
				branch.loc());
	}

	final class BlockConverter {
		SeqBuffer<AnfBind> body = new SeqBuffer<AnfBind>();

		LocalVar convert(CcExp exp) {
			return convert(exp, Optional.empty());
		}
		private LocalVar convert(CcExp exp, Optional<Id> resultName) {
			return switch(exp) {
			case CcExp.CcCnst(ConstValue value, Location loc) ->
					bind(new AnfRhs.Cnst(value), value.type(), loc, resultName);
			case CcExp.CcVar(Id id, Type _, Location _) -> {
				LocalVar source = vars.get(id);
				resultName.ifPresent(alias -> vars.put(alias, source));
				yield source;
			}
			case CcExp.CcDirectApp(Id funId, Seq<CcExp> args, Type type, Location loc) ->
					bind(new AnfRhs.DirectApp(funId, args.map(this::convert)), type, loc, resultName);
			case CcExp.CcClosureApp(CcExp fun, Seq<CcExp> args, Type type, Location loc) -> {
				LocalVar closure = convert(fun);
				yield bind(new AnfRhs.ClosureApp(closure, args.map(this::convert)), type, loc, resultName);
			}
			case CcExp.CcMkCls(Id impl, Seq<CcExp> captures, Type type, Location loc) ->
					bind(new AnfRhs.MakeClosure(impl, captures.map(this::convert)), type, loc, resultName);
			case CcExp.CcIf(CcExp condition, CcExp thenExp, CcExp elseExp, Type type, Location loc) ->
					bind(
							new AnfRhs.If(
									convert(condition),
									convertNestedBlock(thenExp),
									convertNestedBlock(elseExp)),
							type,
							loc,
							resultName);
			case CcExp.CcCase(CcExp targetExp, Seq<CcCaseBranch> branches, Type type, Location loc) -> {
				LocalVar targetAtom = convert(targetExp);
				yield bind(
						new AnfRhs.Case(
								targetAtom,
								branches.map(FunctionConverter.this::convert)),
						type,
						loc,
						resultName);
			}
			case CcExp.CcLet(Id id, CcExp boundExp, CcExp bodyExp, Type _, Location _) -> {
				convert(boundExp, Optional.of(id));
				yield convert(bodyExp, resultName);
			}
			case CcExp.CcRecord(Seq<CcExp.CcRecordField> fields, Type type, Location loc) -> {
				Seq<AnfRhs.AnfRecordField> convertedFields = fields.map(field ->
						new AnfRhs.AnfRecordField(field.name(), convert(field.value())));
				yield bind(new AnfRhs.MakeRecord(convertedFields), type, loc, resultName);
			}
			case CcExp.CcRecordAccess(CcExp record, String field, Type type, Location loc) -> {
				LocalVar target = convert(record);
				yield bind(new AnfRhs.RecordGet(target, field), type, loc, resultName);
			}
			case CcExp.CcRecordUpdate(
					CcExp record,
					Seq<CcExp.CcRecordField> fields,
					Type type,
					Location loc
			) -> {
				LocalVar target = convert(record);
				Seq<AnfRhs.AnfRecordField> convertedFields = fields.map(field ->
						new AnfRhs.AnfRecordField(
								field.name(),
								convert(field.value())));
				yield bind(new AnfRhs.RecordUpdate(target, convertedFields), type, loc, resultName);
			}
			};
		}

		private LocalVar bind(AnfRhs rhs, Type type, Location loc, Optional<Id> resultName) {
			LocalVar dst = new LocalVar(localIdCounter.getAndIncrement(), resultName, type);
			resultName.ifPresent(name -> vars.put(name, dst));
			body.add(new AnfBind(dst, rhs, loc));
			return dst;
		}

	}
}
