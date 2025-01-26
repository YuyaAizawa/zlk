package zlk.recon.constraint;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.recon.Variable;
import zlk.util.TriConsumer;
import zlk.util.TriFunction;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

public class Constraint implements PrettyPrintable {

	public final Category category;
	private final Type type;
	private final Type expected;
	private final zlk.common.Type anno;
	private final Id name;
	private final List<Constraint> ands;
	private final LetConstraint let;

	public enum Category {
		CTrue,
		CEqual,
		CLocal,
		CForeign,
		CPattern,
		CAnd,
		CLet,
		CSaveTheEnvironment
	}

	public record LetConstraint(
			List<Variable> ridids,
			List<Variable> flexes,
			IdMap<Type> headers,
			Constraint headerCon,
			Constraint bodyCon) {}

	public static Constraint ctrue() {
		return new Constraint(Category.CTrue, null, null, null, null, null, null);
	}

	public static Constraint equal(Type type, Type expected) {
		return new Constraint(Category.CEqual, type, expected, null, null, null, null);
	}

	public static Constraint local(Id id, Type expected) {
		return new Constraint(Category.CLocal, null, expected, null, id, null, null);
	}

	public static Constraint foreign(Id id, zlk.common.Type anno, Type expected) {
		return new Constraint(Category.CForeign, null, expected, anno, id, null, null);
	}

	public static Constraint pattern(Type type, Type expected) {
		return new Constraint(Category.CPattern, type, expected, null, null, null, null);
	}

	public static Constraint and(List<Constraint> constraints) {
		return new Constraint(Category.CAnd, null, null, null, null, constraints, null);
	}

	public static Constraint let(List<Variable> ridids, List<Variable> flexes, IdMap<Type> headerAnno, Constraint headerCon,
			Constraint bodyCon) {
		return new Constraint(Category.CLet, null, null, null, null, null,
				new LetConstraint(ridids, flexes, headerAnno, headerCon, bodyCon));
	}

	public static Constraint saveTheEnvironment() {
		return new Constraint(Category.CSaveTheEnvironment, null, null, null, null, null, null);
	}

	public <R> R fold(
			Supplier<? extends R> forTrue,
			BiFunction<? super Type, ? super Type, ? extends R> forEqual,
			BiFunction<? super Id, ? super Type, ? extends R> forLocal,
			TriFunction<? super Id, ? super zlk.common.Type, ? super Type, ? extends R> forForeign,
			BiFunction<? super Type, ? super Type, ? extends R> forPattern,
			Function<? super List<Constraint>, ? extends R> forAnd,
			Function<? super LetConstraint, ? extends R> forLet,
			Supplier<? extends R> forSte) {
		switch (category) {
		case CTrue:
			return forTrue.get();
		case CEqual:
			return forEqual.apply(type, expected);
		case CLocal:
			return forLocal.apply(name, expected);
		case CForeign:
			return forForeign.apply(name, anno, expected);
		case CPattern:
			return forPattern.apply(type, expected);
		case CAnd:
			return forAnd.apply(ands);
		case CLet:
			return forLet.apply(let);
		case CSaveTheEnvironment:
			return forSte.get();
		default:
			throw new IllegalArgumentException("Unexpected value: " + category);
		}
	}

	public void match(
			Runnable forTrue,
			BiConsumer<? super Type, ? super Type> forEqual,
			BiConsumer<? super Id, ? super Type> forLocal,
			TriConsumer<? super Id, ? super zlk.common.Type, ? super Type> forForeign,
			BiConsumer<? super Type, ? super Type> forPattern,
			Consumer<? super List<Constraint>> forAnd,
			Consumer<? super LetConstraint> forLet,
			Runnable forSte) {
		switch (category) {
		case CTrue:
			forTrue.run();
			return;
		case CEqual:
			forEqual.accept(type, expected);
			return;
		case CLocal:
			forLocal.accept(name, expected);
			return;
		case CForeign:
			forForeign.accept(name, anno, expected);
			return;
		case CPattern:
			forPattern.accept(type, expected);
			return;
		case CAnd:
			forAnd.accept(ands);
			return;
		case CLet:
			forLet.accept(let);
			return;
		case CSaveTheEnvironment:
			forSte.run();
			return;
		default:
			throw new IllegalArgumentException("Unexpected value: " + category);
		}
	}

	private Constraint(Category category, Type type, Type expected, zlk.common.Type anno, Id name, List<Constraint> ands,
			LetConstraint let) {
		this.category = category;
		this.type = type;
		this.expected = expected;
		this.anno = anno;
		this.name = name;
		this.ands = ands;
		this.let = let;
	}

	@Override
	public void mkString(PrettyPrinter pp) {
		match(
				() -> pp.append("True"),
				(l, r) -> pp.append("Equal: ").append(l).append(", ").append(r),
				(id, expected) -> pp.append("Local: ").append(id).append(", ").append(expected),
				(id, ty, expected) ->
					pp.append("Foreign: ").append(id).append("::").append(ty)
							.append(", ").append(expected),
				(pat, expected) -> pp.append("Pattern: ").append(pat).append(", ").append(expected),
				list -> {
					pp.append("And:");
					pp.inc();
					list.forEach(c -> pp.endl().append(c));
					pp.dec();
				},
				let -> {
					pp.append("Let:").endl();
					pp.inc();

					pp.append("ridids:");
					let.ridids.forEach(v -> pp.append(" ").append(v));
					pp.endl();

					pp.append("flexes:");
					let.flexes.forEach(v -> pp.append(" ").append(v));
					pp.endl();

					pp.append("headers:");
					if(let.headers.isEmpty()) {
						pp.append(" []");
					} else {
						pp.endl().inc().append(let.headers).dec();
					}
					pp.endl();

					pp.append("headerCons: ");
					let.headerCon.mkString(pp);
					pp.endl();

					pp.append("bodyCon: ");
					let.bodyCon.mkString(pp);
					pp.dec();
				},
				() -> pp.append("SaveTheEnvironment"));
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		pp(sb);
		return sb.toString();
	}
}
