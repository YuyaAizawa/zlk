package zlk.parser;

import static zlk.parser.Token.Kind.ARROW;
import static zlk.parser.Token.Kind.BAR;
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
import static zlk.parser.Token.Kind.TYPE;
import static zlk.parser.Token.Kind.UCID;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import zlk.ast.ATyArrow;
import zlk.ast.ATyAtom;
import zlk.ast.AType;
import zlk.ast.App;
import zlk.ast.Cnst;
import zlk.ast.Constructor;
import zlk.ast.Decl;
import zlk.ast.Exp;
import zlk.ast.If;
import zlk.ast.Let;
import zlk.ast.Module;
import zlk.ast.Union;
import zlk.ast.UnionOrDecl;
import zlk.ast.Var;
import zlk.common.cnst.Bool;
import zlk.parser.Token.Kind;
import zlk.util.Location;
import zlk.util.Position;

/**
 * 構文解析器.
 * <h2>文法</h2>
 * <pre>{@code
 * <module>   ::= module <ucid> $(<topDecl>*)
 * $ The all decls start in the same column. This column called offside column.
 *   Offside column of the topDecl is 1. The elements other than "type" keyword or the declared
 *   identifiers appear to the right of the offside column.
 *
 * <topDecl>  ::= <union>
 *              | <decl>
 *
 * <union>    ::= type <ucid> = <ctor> (| <ctor>)*
 *
 * <ctor>     ::= <ucid> <aType>*
 *
 * <decl>     ::= ($<lcid> : <type>)? $<lcid>+ = <exp> ;
 * $ type annotation and decl pair starts in the same column.
 *
 * <exp>      ::= <aExp>+
 *              | let $<declList> in <exp>
 *              | if <exp> then <exp> else <exp>
 * $ The declList starts to the right column of the "let" keyword.
 *
 * <declList> ::= $(<decl>+)
 * $ The elements other than the declared identifiers appear to the right of the offside column.
 *
 * <aExp>     ::= ( <exp> )
 *              | <constant>
 *              | <lcid>
 *
 * <type>     ::= <aType> (-> <type>)?
 *
 * <aType>    ::= ( <type> )
 *              | <ucid>
 * }</pre>
 */
public class Parser {

	private final Lexer lexer;

	private Token current;
	private Token next;

	private int offsideColumn;

	private Position end;

	public Parser(Lexer lexer) {
		this.lexer = lexer;
		this.current = lexer.nextToken();
		this.next = lexer.nextToken();
		this.offsideColumn = 1;
	}

	public Parser(String fileName) throws IOException {
		this(new Lexer(fileName));
	}

	public Module parse() {
		return parseModule(lexer.getFileName());
	}

	public Module parseModule(String fileName) {
		consume(MODULE);

		String name = parse(UCID);

		List<UnionOrDecl> topDecls = new ArrayList<>();

		this.offsideColumn = 1;
		while(current.kind() != EOF) {
			if(current.kind() == TYPE) {
				topDecls.add(UnionOrDecl.union(parseUnion()));
			} else {
				topDecls.add(UnionOrDecl.decl(parseDecl()));
			}
		}

		return new Module(name, topDecls, fileName);
	}

	public Union parseUnion() {
		if(current.column() != 1) {
			throw new RuntimeException("type declaration must starts column 1");
		}

		Position start = current.pos();

		consume(TYPE);

		String name = parse(UCID);

		if(current.column() <= offsideColumn) {
			throw new RuntimeException(current.pos() + " missing \"=\" on \"" + name + "\"@" + start);
		}

		List<Constructor> ctors = new ArrayList<>();
		consume(EQUAL);
		ctors.add(parseConstructor());
		while(current.kind() == BAR) {
			consume(BAR);
			ctors.add(parseConstructor());
		}

		return new Union(name, ctors, location(start, end));
	}

	private Constructor parseConstructor() {
		Position start = current.pos();

		String name = parse(UCID);

		List<AType> args = new ArrayList<>();
		while(aTypeStartToken(current) && current.column() > offsideColumn) {
			args.add(parseAType());
		}

		return new Constructor(name, args, location(start, end));
	}

	private static boolean aTypeStartToken(Token token) {
		return token.kind() == UCID || token.kind() == LPAREN;
	}

	public Decl parseDecl() {
		if(current.column() != offsideColumn) {
			throw new RuntimeException(
					current.pos() + " declaration must starts to the right column of the \"let\"");
		}

		Position start = current.pos();

		String name = parse(LCID);

		if(current.column() <= offsideColumn) {
			throw new RuntimeException(current.pos() + " missing \":\" or \"=\" on \"" + name + "\"@" + start);
		}

		Optional<AType> anno;
		if(current.kind() == COLON) {
			nextToken();
			if(current.column() <= offsideColumn) {
				throw new RuntimeException(current.pos() + " missing type on \"" + name + "\"@" + start);
			}
			anno = Optional.of(parseType());
			if(current.column() != offsideColumn) {
				throw new RuntimeException(
						current.pos() + " declaration must starts to the right column of the \"let\"");
			}
			if(!parse(LCID).equals(name)) {
				throw new RuntimeException(
						current.pos() + " declatarion must have the same name with annotation");
			}
		} else {
			anno = Optional.empty();
		}

		// TODO parse pattern
		List<Var> args = new ArrayList<>();
		while(current.kind() == LCID && current.column() > offsideColumn) {
			args.add(new Var(parse(LCID), location(start, end)));
		}

		if(current.column() <= offsideColumn) {
			throw new RuntimeException(current.pos() + " missing \"=\" on \"" + name + "\"@" + start);
		}
		consume(EQUAL);

		if(current.column() <= offsideColumn) {
			throw new RuntimeException(current.pos() + " missing body on \"" + name + "\"@" + start);
		}
		Exp body = parseExp();

		return new Decl(name, anno, args, body, location(start, end));
	}

	public Exp parseExp() {
		Position start = current.pos();
		if(aExpStartToken(current)) {
			Exp first = parseAExp();

			if(aExpStartToken(current) && current.column() > offsideColumn) {
				List<Exp> exps = new ArrayList<>();
				exps.add(first);

				while(aExpStartToken(current) && current.column() > offsideColumn) {
					exps.add(parseAExp());
				}
				return new App(exps, location(start, end));
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
		Position start = current.pos();
		consume(LET);

		List<Decl> declList;
		if(current.kind() != IN) {
			if(current.column() <= offsideColumn) {
				throw new RuntimeException(current.pos() + " declaration list must be indented");
			}

			int oldOffsideColumn = offsideColumn;
			offsideColumn = current.column();
			declList = parseDeclList();
			offsideColumn = oldOffsideColumn;

		} else {
			declList = List.of();
		}

		if(current.column() <= offsideColumn) {
			throw new RuntimeException(current.pos() + " missing \"in\" for \"let\"@" + start);
		}
		consume(IN);

		if(current.column() <= offsideColumn) {
			throw new RuntimeException(current.pos() + " missing body for \"let\"@" + start);
		}
		Exp body = parseExp();

		return new Let(declList, body, location(start, end));
	}

	private Exp parseIfExp() {
		Position start = current.pos();
		consume(IF);

		Exp c = parseExp();

		consume(THEN);

		Exp t = parseExp();

		consume(ELSE);

		Exp e = parseExp();

		return new If(c, t, e, location(start, end));
	}

	private Exp parseAExp() {
		Position start = current.pos();
		return switch(current.kind()) {

		case LPAREN -> parseParenExp();

		case TRUE -> {
			nextToken();
			yield new Cnst(Bool.TRUE, location(start, end));
		}

		case FALSE -> {
			nextToken();
			yield new Cnst(Bool.FALSE, location(start, end));
		}

		case DIGITS -> new Cnst(Integer.valueOf(parse(DIGITS)), location(start, end));

		case LCID -> new Var(parse(LCID), location(start, end));

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

	private List<Decl> parseDeclList() {
		List<Decl> decls = new ArrayList<>();
		while(current.kind() == LCID) {
			decls.add(parseDecl());
		}
		return decls;
	}

	private AType parseType() {
		Position start = current.pos();

		AType ty = parseAType();

		if(current.kind() == ARROW) {
			nextToken();
			return new ATyArrow(ty, parseType(), location(start, end));
		} else {
			return ty;
		}
	}

	private AType parseAType() {
		Position start = current.pos();
		return switch(current.kind()) {
		case LPAREN -> parseParenType();
		case UCID -> {
			AType type = new ATyAtom(current.value(), location(start, end));
			nextToken();
			yield type;
		}
		default -> throw new RuntimeException("type expected");
		};
	}

	private AType parseParenType() {
		consume(LPAREN);

		AType type = parseAType();

		consume(RPAREN);

		return type;
	}

	private void nextToken() {
		end = current.endPos();
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
					current.pos() + " cannot parse. "
					+ ", expected: " + expected
					+ ", actual: " + current.kind());
		}
	}

	private void consume(Kind expected) {
		if(current.kind() == expected) {
			nextToken();
		} else {
			throw new RuntimeException(
					current.pos() + " cannot consume. "
					+ "expected: " + expected
					+ ", actual: " + current.kind());
		}
	}

	private Location location(Position start, Position end) {
		return new Location(lexer.getFileName(), start, end);
	}
}
