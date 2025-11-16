package zlk.tester;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import zlk.ast.AnType;
import zlk.common.Type;
import zlk.common.Type.Arrow;
import zlk.common.Type.CtorApp;
import zlk.common.Type.Var;
import zlk.common.id.Id;
import zlk.common.id.IdMap;
import zlk.parser.Lexer;
import zlk.parser.Parser;

public final class TypeTester {

	private Type ty;
	private Id moduleId;
	private IdMap<Type> tysInModule;

	TypeTester(Type ty, Id moduleId, IdMap<Type> tysInModule) {
		this.ty = ty;
		this.moduleId = moduleId;
		this.tysInModule = tysInModule;
	}

	public void is(String expected) {
		Type expectedTy = simpleEval(new Parser(new Lexer("TypeTester", expected)).parseType());
		expectedTy = importFromModule(expectedTy);
		is(expectedTy);
	}
	public void is(Type expected) {
		assertEquals(expected, ty);
	}

	private Type simpleEval(AnType aTy) {
		return switch (aTy) {
		case AnType.Unit _ -> Type.UNIT;
		case AnType.Var(String name, _) -> new Type.Var(name);
		case AnType.Type(String ctor, List<AnType> args, _) -> {
			Id ctor_ = Id.fromCanonicalName(ctor);
			List<Type> args_ = args.stream().map(arg -> simpleEval(arg)).toList();
			yield new Type.CtorApp(ctor_, args_);
		}
		case AnType.Arrow(AnType arg, AnType ret, _) -> new Type.Arrow(simpleEval(arg), simpleEval(ret));
		};
	}

	/**
	 * モジュール内で定義した型はモジュール名なしにしたい
	 * @param ty
	 */
	private Type importFromModule(Type ty) {
		return switch(ty) {
			case CtorApp(Id id, List<Type> typeArguments) -> {
				yield new CtorApp(condidate(id), typeArguments.stream().map(t -> importFromModule(t)).toList());
			}
			case Arrow(Type arg, Type ret) -> {
				yield new Arrow(importFromModule(arg), importFromModule(ret));
			}
			case Var _ -> { yield ty; }
		};
	}
	private Id condidate(Id id) {
		Id id_ = Id.fromCanonicalName(this.moduleId.canonicalName()+Id.SEPARATOR+id.canonicalName());  // TODO: 何とかする
		return this.tysInModule.containsKey(id_) ? id_ : id;
	}

	@Override
	public String toString() {
		return ty.buildString();
	}
}
