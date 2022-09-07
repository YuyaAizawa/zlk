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
import zlk.util.Location;

/*
 * 文法
 * <compileUnit> : <module>
 *
 * <module> : module <ucid> <declList>
 *
 * <exp> : <aExp>+
 *       | let $<declList> in <exp>
 *       | if <exp> then <exp> else <exp>
 * $ The declList starts to the right column of the "let" keyword.
 *
 * <declList> : $(<decl>+)
 * $ The all decls start in the same column. This column called decl column of the declList. The elements other than the
 *   declared identifiers appear to the right of the decl column.
 *
 * <decl> : <lcid>+ \: <type> = <exp> ;
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

	private int declColumn;
	
	public Parser(Lexer lexer) {
		this.lexer = lexer;
		this.current = lexer.nextToken();
		this.next = lexer.nextToken();
		this.declColumn = 1;
	}

	public Module parse() {
		return parseModule(lexer.getFileName());
	}

	public Module parseModule(String fileName) {
		consume(MODULE);

		String name = parse(UCID);

		this.declColumn = 1;
		List<Decl> decls = parseDeclList();
		
		if (current.kind() != EOF) {
			throw new RuntimeException(current.location() + "token remained.");
		}

		return new Module(name, decls, fileName);
	}
	
	public List<Decl> parseDeclList() {
		List<Decl> decls = new ArrayList<>();
		while(current.kind() == LCID) {
			decls.add(parseDecl());
		}
		return decls;
	}
	
	public Decl parseDecl() {
		if(current.column() != declColumn) {
			throw new RuntimeException(
					current.location() + " declaration must starts to the right column of the \"let\"");
		}
		
		Location location = current.location();
		String name = parse(LCID);

		List<String> args = new ArrayList<>();
		while(current.kind() == LCID && current.column() > declColumn) {
			args.add(parse(LCID));
		}

		if(current.column() <= declColumn) {
			throw new RuntimeException(current.location() + " missing \":\" on \"" + name + "\"@" + location);
		}
		consume(COLON);

		if(current.column() <= declColumn) {
			throw new RuntimeException(current.location() + " missing type on \"" + name + "\"@" + location);
		}
		Type type = parseType();

		if(current.column() <= declColumn) {
			throw new RuntimeException(current.location() + " missing \"=\" on \"" + name + "\"@" + location);
		}
		consume(EQUAL);

		if(current.column() <= declColumn) {
			throw new RuntimeException(current.location() + " missing body on \"" + name + "\"@" + location);
		}
		Exp body = parseExp();

		return new Decl(name, args, type, body);
	}

	public Exp parseExp() {

		if(aExpStartToken(current)) {
			Exp first = parseAExp();

			if(aExpStartToken(current) && current.column() > declColumn) {
				List<Exp> exps = new ArrayList<>();
				exps.add(first);
				while(aExpStartToken(current) && current.column() > declColumn) {
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
		Location letLocation = current.location();
		consume(LET);
		
		List<Decl> declList;
		if(current.kind() != IN) {
			if(current.column() <= declColumn) {
				throw new RuntimeException(current.location() + " declaration list must be indented");
			}

			int oldDeclColumn = declColumn;
			declColumn = current.column();
			declList = parseDeclList();
			declColumn = oldDeclColumn;
		} else {
			declList = List.of();
		}
		
		if(current.column() <= declColumn) {
			throw new RuntimeException(current.location() + " missing \"in\" for \"let\"@" + letLocation);
		}
		consume(IN);

		if(current.column() <= declColumn) {
			throw new RuntimeException(current.location() + " missing body for \"let\"@" + letLocation);
		}
		Exp body = parseExp();

		return new Let(declList, body);
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
					current.location() + " cannot parse. "
					+ ", expected: " + expected
					+ ", actual: " + current.kind());
		}
	}

	private void consume(Kind expected) {
		if(current.kind() == expected) {
			nextToken();
		} else {
			throw new RuntimeException(
					current.location() + " cannot consume. "
					+ "expected: " + expected
					+ ", actual: " + current.kind());
		}
	}
}
