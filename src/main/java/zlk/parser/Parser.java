package zlk.parser;

import static zlk.parser.Token.Kind.ARROW;
import static zlk.parser.Token.Kind.COLON;
import static zlk.parser.Token.Kind.DIGITS;
import static zlk.parser.Token.Kind.ELSE;
import static zlk.parser.Token.Kind.EOF;
import static zlk.parser.Token.Kind.EQUAL;
import static zlk.parser.Token.Kind.IF;
import static zlk.parser.Token.Kind.IN;
import static zlk.parser.Token.Kind.LCID;
import static zlk.parser.Token.Kind.LET;
import static zlk.parser.Token.Kind.LPAREN;
import static zlk.parser.Token.Kind.MODULE;
import static zlk.parser.Token.Kind.RPAREN;
import static zlk.parser.Token.Kind.SEMICOLON;
import static zlk.parser.Token.Kind.THEN;
import static zlk.parser.Token.Kind.UCID;

import java.util.ArrayList;
import java.util.List;

import zlk.ast.App;
import zlk.ast.Const;
import zlk.ast.Decl;
import zlk.ast.Exp;
import zlk.ast.Id;
import zlk.ast.If;
import zlk.ast.Let;
import zlk.ast.Module;
import zlk.common.TyArrow;
import zlk.common.Type;
import zlk.parser.Token.Kind;

/*
 * 文法
 * <compileUnit> : <module>
 *
 * <module> : module <ucid> <decl>+
 *
 * <decl> : <lcid>+ \: <type> = <exp> ;
 *
 * <exp> : <aExp>+
 *       | let decl in <exp>
 *       | if <exp> then <exp> else <exp>
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

	public Module parse() {
		return parseModule(lexer.getFileName());
	}

	public Module parseModule(String fileName) {
		consume(MODULE);

		String name = parse(UCID);

		List<Decl> decls = new ArrayList<>();
		while(next.kind() != EOF) {
			decls.add(parseDecl());
		}

		return new Module(name, decls, fileName);
	}

	public Decl parseDecl() {
		String name = parse(LCID);

		List<String> args = new ArrayList<>();
		while(current.kind() == LCID) {
			args.add(parse(LCID));
		}

		consume(COLON);

		Type type = parseType();

		consume(EQUAL);

		Exp body = parseExp();

		consume(SEMICOLON);

		return new Decl(name, args, type, body);
	}

	public Exp parseExp() {

		if(aExpStartToken(current)) {
			Exp first = parseAExp();

			if(aExpStartToken(current)) {
				List<Exp> exps = new ArrayList<>();
				exps.add(first);
				while(aExpStartToken(current)) {
					exps.add(parseAExp());
				}
				return new App(exps);
			} else {
				return first;
			}
		}

		return switch(current.kind()) {
		case LET -> parseLetExp();
		case IF -> parseIfExp();
		default -> throw new RuntimeException("not exp");
		};
	}

	private Exp parseLetExp() {
		consume(LET);

		Decl decl = parseDecl();

		consume(IN);

		Exp body = parseExp();

		return new Let(decl, body);
	}

	private Exp parseIfExp() {
		consume(IF);

		Exp c = parseExp();

		consume(THEN);

		Exp t = parseExp();

		consume(ELSE);

		Exp e = parseExp();

		return new If(c, t, e);
	}

	private Exp parseAExp() {
		return switch(current.kind()) {

		case LPAREN -> parseParenExp();

		case TRUE -> {
			nextToken();
			yield Const.bool(true);
		}

		case FALSE -> {
			nextToken();
			yield Const.bool(false);
		}

		case DIGITS -> Const.i32(Integer.valueOf(parse(DIGITS)));

		case LCID -> new Id(parse(LCID));

		default -> throw new RuntimeException("not a exp");
		};
	}

	private static boolean aExpStartToken(Token token) {
		switch (token.kind()) {
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

	private Exp parseParenExp() {
		consume(LPAREN);

		Exp exp = parseExp();

		consume(RPAREN);

		return exp;
	}

	private Type parseType() {
		Type ty = parseAType();

		if(current.kind() == ARROW) {
			nextToken();
			return new TyArrow(ty, parseType());
		} else {
			return ty;
		}
	}

	private Type parseAType() {
		return switch(current.kind()) {
		case LPAREN -> parseParenType();
		case UCID -> {
			Type type = switch(current.value()) {
			case "Bool" -> Type.bool;
			case "I32"  -> Type.i32;
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
		consume(LPAREN);

		Type type = parseAType();

		consume(RPAREN);

		return type;
	}

	private void nextToken() {
		current = next;
		next = lexer.nextToken();
	}

	private String parse(Kind expected) {
		if(current.kind() == expected) {
			String ret = current.value();
			nextToken();
			return ret;
		} else {
			throw new RuntimeException(
					"cannot consume. expected: "+expected+" actual: "+current.kind());
		}
	}

	private void consume(Kind expected) {
		if(current.kind() == expected) {
			nextToken();
		} else {
			throw new RuntimeException(
					"cannot consume. expected: "+expected+" actual: "+current.kind());
		}
	}
}
