package zlk.parser;

import java.util.ArrayList;
import java.util.List;

import zlk.ast.App;
import zlk.ast.CompileUnit;
import zlk.ast.Const;
import zlk.ast.Decl;
import zlk.ast.Exp;
import zlk.ast.I32;
import zlk.ast.Id;
import zlk.common.TyArrow;
import zlk.common.Type;

/*
 * 文法
 * <compileUnit> : module <ucid> <decl>+
 *
 * <decl> : <lcid> \: <type> = <exp> ;
 *
 * <exp> : <aExp>+
 *
 * <aExp> : \( <exp> \)
 *        | <constant>
 *        | <lcid>
 *
 * <type> : <aType> ( -> <type> )?
 *
 * <aType> : \( <type> \)
 *         | <ucid>
 */
public class Parser {

	private final Lexer lexer;

	private Token current;
	private Token next;

	public Parser(Lexer lexer) {
		this.lexer = lexer;
		this.current = lexer.nextToken();
		this.next = lexer.nextToken();
	}

	private void nextToken() {
		current = next;
		next = lexer.nextToken();
	}

	private void consume(Token.Kind expected) {
		if(current.type() == expected) {
			nextToken();
		} else {
			throw new RuntimeException(
					"cannot consume. expected: "+expected+" actual: "+current.type());
		}
	}

	public CompileUnit parse() {
		consume(Token.Kind.MODULE);

		String name = current.value();
		consume(Token.Kind.UCID);

		List<Decl> decls = new ArrayList<>();
		while(next.type() != Token.Kind.EOF) {
			decls.add(parseDecl());
		}

		return new CompileUnit(name, decls);
	}

	public Decl parseDecl() {
		String name = current.value();
		consume(Token.Kind.LCID);

		consume(Token.Kind.COLON);

		Type type = parseType();

		consume(Token.Kind.EQUAL);

		Exp body = parseExp();

		consume(Token.Kind.SEMICOLON);

		return new Decl(name, type, body);
	}

	public Exp parseExp() {

		if(aExpStartToken(current)) {
			Exp left = parseATerm();
			while(aExpStartToken(current)) {
				left = new App(left, parseATerm());
			}
			return left;
		}

		throw new RuntimeException();
	}

	private Exp parseATerm() {
		return switch(current.type()) {

		case LPAREN -> parseParenTerm();

		case TRUE -> {
			nextToken();
			yield Const.bool(true);
		}

		case FALSE -> {
			nextToken();
			yield Const.bool(false);
		}

		case DIGITS -> {
			I32 i32 = Const.i32(Integer.valueOf(current.value()));
			nextToken();
			yield i32;
		}

		case LCID -> {
			Id id = new Id(current.value());
			nextToken();
			yield id;
		}

		default -> throw new RuntimeException("not a exp");
		};
	}

	private static boolean aExpStartToken(Token token) {
		switch (token.type()) {
		case LCID:
		case DIGITS:
		case TRUE:
		case FALSE:
		case LPAREN:
			return true;
		default:
			return false;
		}
	}

	private Exp parseParenTerm() {
		consume(Token.Kind.LPAREN);
		Exp term = parseExp();
		consume(Token.Kind.RPAREN);
		return term;
	}

	private Type parseType() {
		Type ty = parseAType();

		if(current.type() == Token.Kind.ARROW) {
			nextToken();
			return new TyArrow(ty, parseType());
		} else {
			return ty;
		}
	}

	private Type parseAType() {
		return switch(current.type()) {
		case LPAREN -> parseParenType();
		case UCID -> {
			Type type = switch(current.value()) {
			case "Bool" -> Type.BOOL;
			case "I32"  -> Type.I32;
			default -> throw new RuntimeException(
					"cannot accept as type: \""+current.value()+"\"");
			};
			nextToken();
			yield type;
		}
		default -> throw new RuntimeException("type expected");
		};
	}

	private Type parseParenType() {
		consume(Token.Kind.LPAREN);
		Type type = parseAType();
		consume(Token.Kind.RPAREN);
		return type;
	}
}
