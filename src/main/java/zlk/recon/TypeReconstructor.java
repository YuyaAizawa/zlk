package zlk.recon;

import static zlk.util.ErrorUtils.neverHappen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import zlk.common.id.Id;
import zlk.common.type.Type;
import zlk.idcalc.IcDecl;
import zlk.idcalc.IcExp;

public final class TypeReconstructor {
	private static final TypeSchema UNIT = new TsBase(Id.fromCanonicalName("()"));
	private static final TypeSchema BOOL = new TsBase(Id.fromCanonicalName("Bool"));
	private static final TypeSchema I32  = new TsBase(Id.fromCanonicalName("I32"));

	private LetBounds delta = new LetBounds();

	public void addBuiltin(Id id, Type ty) {
		delta.add(id, typeToSchema(ty));
	}

	public void recon(IcDecl decl) {
		TypeSchema tau = ptsDef(decl.body()).tau;
		delta.add(decl.id(), tau);
		System.out.println(decl.id()+": "+tau);
	}

	public PtsResult ptsDef(IcExp exp) {
		return exp.fold(
				cnst -> new PtsResult(new TypeEnvironment(), typeToSchema(cnst.type())),
				var  -> {
					if(delta.containsKey(var.id())) {
						return new PtsResult(new TypeEnvironment(), delta.freshInst(var.id()));
					} else {
						TsVar fresh = new TsVar();
						TypeEnvironment e = new TypeEnvironment();
						e.add(var.id(), fresh);
						return new PtsResult(e, fresh);
					}
				},
				abs -> {
					Id x = abs.id();
					PtsResult bodyResult = ptsDef(abs.body());
					TypeEnvironment gamma = bodyResult.gamma;
					TypeSchema tau = bodyResult.tau;
					if(gamma.containsVar(x)) {
						TypeSchema gx = gamma.remove(x);
						return new PtsResult(gamma, new TsArrow(gx, tau));
					} else {
						return new PtsResult(gamma, new TsArrow(new TsVar(), tau));
					}
				},
				app -> {
					PtsResult r1 = ptsDef(app.fun());
					PtsResult r2 = ptsDef(app.arg());
					TypeEnvironment g1 = r1.gamma;
					TypeSchema t1 = r1.tau;
					TypeEnvironment g2 = r2.gamma;
					TypeSchema t2 = r2.tau;

					TypeEquations e = new TypeEquations();
					intersection(g1.domain(), g2.domain())
							.forEach(x -> e.push(g1.get(x), g2.get(x)));
					TypeSchema t = new TsVar();
					e.push(t1, new TsArrow(t2, t));
					Substitution s = u(e);

					TypeEnvironment g = new TypeEnvironment();
					g1.forEach((x, tau)  -> g.addIfNotContains(x, s.apply(tau)));
					g2.forEach((x, tau)  -> g.addIfNotContains(x, s.apply(tau)));
					return new PtsResult(g, s.apply(t));
				},
				if_ -> {
					// TODO 条件式と分岐で整合性確認を分離
					PtsResult rCond = ptsDef(if_.cond());
					PtsResult rThen = ptsDef(if_.exp1());
					PtsResult rElse = ptsDef(if_.exp2());
					TypeEnvironment gc = rCond.gamma;
					TypeSchema tc = rCond.tau;
					TypeEnvironment gt = rThen.gamma;
					TypeSchema tt = rThen.tau;
					TypeEnvironment ge = rElse.gamma;
					TypeSchema te = rElse.tau;

					TypeEquations e = new TypeEquations();
					intersection(gc.domain(), gt.domain())
							.forEach(x -> e.push(gc.get(x), gt.get(x)));
					intersection(gc.domain(), ge.domain())
							.forEach(x -> e.push(gc.get(x), ge.get(x)));
					intersection(gt.domain(), ge.domain())
							.forEach(x -> e.push(gt.get(x), ge.get(x)));
					e.push(tt, te);
					e.push(tc, BOOL);
					Substitution s = u(e);

					TypeEnvironment g = new TypeEnvironment();
					gc.forEach((x, tau)  -> g.addIfNotContains(x, s.apply(tau)));
					gt.forEach((x, tau)  -> g.addIfNotContains(x, s.apply(tau)));
					ge.forEach((x, tau)  -> g.addIfNotContains(x, s.apply(tau)));
					return new PtsResult(g, s.apply(tt));
				},
				let -> {
					PtsResult r1 = ptsDef(let.decl().body());
					System.out.println(let.decl().id()+": "+r1.tau);
					if(r1.gamma.isEmpty()) {
						delta.add(let.decl().id(), r1.tau);
						return ptsDef(let.body());
					} else {
						throw new IllegalStateException(r1.gamma+"");
					}
				});
	}

	private static List<Id> intersection(List<Id> vars1, List<Id> vars2) {
		return vars1.stream()
				.filter(vars2::contains)
				.toList();
	}

	private static TypeSchema typeToSchema(Type type) {
		return type.fold(
				unit -> UNIT,
				bool -> BOOL,
				i32 -> I32,
				arrow -> new TsArrow(typeToSchema(arrow.arg()), typeToSchema(arrow.ret())));
	}

	private record PtsResult(TypeEnvironment gamma, TypeSchema tau) {}

	private static Substitution u(TypeEquations e) {
		TypeEquations s = new TypeEquations();

		while(!e.isEmpty()) {
			e.pop((l, r) -> {
				if(l.equals(r)) {
					// nothing to do
				} else {
					l.match(
						alpha -> {
							if(r.contains(alpha)) {
								// failure
								throw new IllegalArgumentException();
							} else {
								e.substitute(alpha, r);
								s.substitute(alpha, r);
								s.push(alpha, r);
							}
						},
						base -> {
							r.match(
									alpha -> {
										e.substitute(alpha, base);
										s.substitute(alpha, base);
										s.push(alpha, base);
									},
									base2 -> {
										// failure
										throw new IllegalArgumentException();
									},
									arrow -> {
										// failure
										throw new IllegalArgumentException();
									});
						},
						arrow -> {
							r.match(
									alpha -> {
										e.substitute(alpha, arrow);
										s.substitute(alpha, arrow);
										s.push(alpha, arrow);
									},
									base -> {
										// failure
										throw new IllegalArgumentException();
									},
									arrow2 -> {
										e.push(arrow.arg(), arrow2.arg());
										e.push(arrow.ret(), arrow2.ret());
									});
						});
				}
			});
		}

		Map<Integer, TypeSchema> result = new HashMap<>(s.size());
		s.forEach((l,r) -> {
			if(l instanceof TsVar var) {
				result.put(var.id(), r);
			} else {
				neverHappen("left side must be type variable");
			}
		});
		return new Substitution(result);
	}
}
