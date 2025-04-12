package zlk.parser;

import static zlk.parser.Token.Kind.ARROW;
import static zlk.parser.Token.Kind.BAR;
import static zlk.parser.Token.Kind.BR;
import static zlk.parser.Token.Kind.CASE;
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
import static zlk.parser.Token.Kind.OF;
import static zlk.parser.Token.Kind.RPAREN;
import static zlk.parser.Token.Kind.THEN;
import static zlk.parser.Token.Kind.TYPE;
import static zlk.parser.Token.Kind.UCID;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import zlk.ast.AType;
import zlk.ast.CaseBranch;
import zlk.ast.Constructor;
import zlk.ast.Decl;
import zlk.ast.Decl.TypeDecl;
import zlk.ast.Decl.ValDecl;
import zlk.ast.Exp;
import zlk.ast.Exp.App;
import zlk.ast.Exp.Case;
import zlk.ast.Exp.Cnst;
import zlk.ast.Exp.If;
import zlk.ast.Exp.Let;
import zlk.ast.Exp.Var;
import zlk.ast.Module;
import zlk.ast.Pattern;
import zlk.common.ConstValue;
import zlk.parser.Token.Kind;
import zlk.util.Location;
import zlk.util.Position;

/**
 * 構文解析器.
 * <h2>文法</h2>
 * <pre>{@code
 * <h3>コンパイル単位</h3>
 * <module>   ::= module <ucid> <topDecl>*
 *
 * <topDecl>  ::= <typeDecl>
 *              | <funDecl>
 *
 * <h3>宣言</h3>
 * <typeDecl> ::= type <ucid> = <ctor> (| <ctor>)*
 *
 * <ctor>     ::= <ucid> <aType>*
 *
 * <funDecl>  ::= (val <lcid> : <type>)? val <lcid>+ = <exp>
 *
 * <h3>式
 * <exp>      ::= <aExp>+
 *              | let <funDecl>+ in <exp>
 *              | if <exp> then <exp> else <exp>
 *              | case <exp> of (| <pattern> -> <exp>)+
 *
 * <aExp>     ::= ( <exp> )
 *              | <aLiteral>
 *              | <lcid>
 *
 * <h3>パターン</h3>
 * <pattern>  ::= <ucid> <aPattern>*
 *              | <aPattern>
 *
 * <aPattern> ::= ( <pattern> )
 *              | <lcid>
 *
 * <h3>型注釈</h3>
 * <type>     ::= <aType> (-> <aType>)*
 *
 * <aType>    ::= ( <type> )
 *              | <ucid>
 * }</pre>
 */
public class Parser {

	private static final EnumSet<Kind> cancelBr = EnumSet.of(
			BAR,
			THEN,
			ELSE,
			ARROW);

	private final Lexer lexer;

	private Token current;
	private Token next;

	private Position end;

	public Parser(Lexer lexer) {
		this.lexer = lexer;
		this.current = lexer.nextToken();
		this.next = lexer.nextToken();
	}

	public Parser(String fileName) throws IOException {
		this(new Lexer(fileName));
	}

	public Module parse() {
		return parseModule(lexer.getFileName().split("\\.")[0]);
	}

	public Module parseModule(String fileName) {
		consume(MODULE);

		String name = parse(UCID);

		consume(BR);
		while(maybeConsume(BR));

		List<Decl> topDecls = new ArrayList<>();
		while(current.kind() != EOF) {
			if(current.kind() == TYPE) {
				topDecls.add(parseTypeDecl());
			} else {
				topDecls.add(parseValDecl());
			}
			consume(BR);
			while(maybeConsume(BR));
		}

		return new Module(name, topDecls, fileName);
	}

	public TypeDecl parseTypeDecl() {
		Position start = current.pos();

		consume(TYPE);

		String name = parse(UCID);

		consume(EQUAL);

		maybeConsume(BAR);

		List<Constructor> ctors = new ArrayList<>();
		ctors.add(parseConstructor());
		while(current.kind() == BAR) {
			consume(BAR);
			ctors.add(parseConstructor());
		}

		return new TypeDecl(name, ctors, location(start, end));
	}

	private Constructor parseConstructor() {
		Position start = current.pos();

		String name = parse(UCID);

		List<AType> args = new ArrayList<>();
		while(aTypeStartToken(current)) {
			args.add(parseAType());
		}

		return new Constructor(name, args, location(start, end));
	}

	private static boolean aTypeStartToken(Token token) {
		return token.kind() == UCID || token.kind() == LPAREN;
	}

	public ValDecl parseValDecl() {
		Position start = current.pos();

		String name = parse(LCID);

		Optional<AType> anno;
		if(maybeConsume(COLON)) {
			anno = Optional.of(parseType());

			consume(BR);

			if(!parse(LCID).equals(name)) {
				throw new RuntimeException(
						current.pos() + " declatarion must have the same name with annotation");
			}
		} else {
			anno = Optional.empty();
		}

		List<Var> args = new ArrayList<>();
		while(current.kind() == LCID) {
			args.add(new Var(parse(LCID), location(start, end)));
		}

		consume(EQUAL);

		maybeConsume(BR);

		Exp body = parseExp();

		return new ValDecl(name, anno, args, body, location(start, end));
	}

	public Exp parseExp() {
		Position start = current.pos();
		if(aExpStartToken(current)) {
			Exp first = parseAExp();

			if(aExpStartToken(current)) {
				List<Exp> exps = new ArrayList<>();
				exps.add(first);

				while(aExpStartToken(current)) {
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
		case CASE -> parseCaseExp();
		default -> throw new RuntimeException("not exp");
		};
	}

	private static boolean aExpStartToken(Token token) {
		switch (token.kind()) {
		case LCID:
		case UCID:
		case DIGITS:
		case TRUE:
		case FALSE:
		case LPAREN:
			return true;
		default:
			return false;
		}
	}

	private Exp parseAExp() {
		Position start = current.pos();
		return switch(current.kind()) {

		case LPAREN -> parseParenExp();

		case TRUE -> {
			nextToken();
			yield new Cnst(ConstValue.TRUE, location(start, end));
		}

		case FALSE -> {
			nextToken();
			yield new Cnst(ConstValue.FALSE, location(start, end));
		}

		case DIGITS -> new Cnst(Integer.valueOf(parse(DIGITS)), location(start, end));

		case LCID -> new Var(parse(LCID), location(start, end));
		case UCID -> new Var(parse(UCID), location(start, end));

		default -> throw new RuntimeException("not a exp");
		};
	}

	private Exp parseLetExp() {
		Position start = current.pos();

		consume(LET);

		List<ValDecl> declList = new ArrayList<>();
		if(maybeConsume(BR)) {
			do {
				declList.add(parseValDecl());

				consume(BR);
			} while(current.kind() != IN);

			consume(IN);

			consume(BR);
		} else {
			declList.add(parseValDecl());

			consume(IN);

			maybeConsume(BR);
		}

		Exp body = parseExp();

		return new Let(declList, body, location(start, end));
	}

	private Exp parseIfExp() {
		Position start = current.pos();
		consume(IF);

		maybeConsume(BR);

		Exp c = parseExp();

		consume(THEN);

		maybeConsume(BR);

		Exp t = parseExp();

		consume(ELSE);

		maybeConsume(BR);

		Exp e = parseExp();

		return new If(c, t, e, location(start, end));
	}

	private Exp parseCaseExp() {
		Position start = current.pos();

		consume(CASE);

		Exp exp = parseExp();

		consume(OF);

		List<CaseBranch> caseBranches = new ArrayList<>();
		do {
			caseBranches.add(parseCaseBranch());
		} while(current.kind() == BAR);

		return new Case(exp, caseBranches, location(start, end));
	}

	private CaseBranch parseCaseBranch() {
		Position start = current.pos();

		consume(BAR);

		Pattern pattern = parsePattern();

		consume(ARROW);

		maybeConsume(BR);

		Exp body = parseExp();

		return new CaseBranch(pattern, body, location(start, end));
	}

	private Pattern parsePattern() {
		Position start = current.pos();

		if(current.kind() == UCID) {
			String name = parse(UCID);

			List<Pattern> args = new ArrayList<>();
			while(current.kind() != ARROW) {
				args.add(parseAPattern());
			}
			return new Pattern.Ctor(name, args, location(start, end));
		}

		return parseAPattern();
	}

	private Pattern parseAPattern() {
		Position start = current.pos();
		Pattern result;

		if(current.kind() == LPAREN) {
			consume(LPAREN);

			result = parsePattern();

			consume(RPAREN);
		} else {
			result = new Pattern.Var(parse(LCID), location(start, end));
		}
		return result;
	}

	private Exp parseParenExp() {
		consume(LPAREN);

		Exp exp = parseExp();

		consume(RPAREN);

		return exp;
	}

	private AType parseType() {
		Position start = current.pos();

		AType ty = parseAType();

		if(current.kind() == ARROW) {
			nextToken();
			return new AType.Arrow(ty, parseType(), location(start, end));
		} else {
			return ty;
		}
	}

	private AType parseAType() {
		Position start = current.pos();
		return switch(current.kind()) {
		case LPAREN -> parseParenType();
		case UCID -> {
			AType type = new AType.Atom(current.value(), location(start, end));
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

		if(current.kind()==BR && cancelBr.contains(next.kind())) {
			current = next;
			next = lexer.nextToken();
		}
	}

	private String parse(Kind expected) {
		if(current.kind() == expected) {
			String ret = current.value();
			nextToken();
			return ret;
		} else {
			throw new RuntimeException(
					current.pos() + " cannot parse. "
					+ "expected: " + expected
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

	private boolean maybeConsume(Kind expected) {
		if(current.kind() == expected) {
			nextToken();
			return true;
		}
		return false;
	}

	private Location location(Position start, Position end) {
		return new Location(lexer.getFileName(), start, end);
	}
}
