package zlk.parser;

import static zlk.parser.Peg.choice;
import static zlk.parser.Peg.kind;
import static zlk.parser.Peg.lazy;
import static zlk.parser.Peg.optional;
import static zlk.parser.Peg.plus;
import static zlk.parser.Peg.sequence;
import static zlk.parser.Peg.star;
import static zlk.util.ErrorUtils.todo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import zlk.ast.AnType;
import zlk.ast.CaseBranch;
import zlk.ast.Constructor;
import zlk.ast.Decl;
import zlk.ast.Decl.TypeDecl;
import zlk.ast.Exp;
import zlk.ast.Module;
import zlk.ast.Pattern;
import zlk.common.Location;
import zlk.common.LocationHolder;
import zlk.parser.Token.Kind;


/**
 * 構文解析器．
 *
 * <h2>文法</h2>
 * ※空白文字の記述を決めかねているためその点正確でない
 * <pre>{@code
 * <h3>コンパイル単位</h3>
 * <module>     ::= module <ucid> <topDecl>*
 *
 * <topDecl>    ::= <tyDecl>
 *                | <valDecl>
 *
 * <h3>宣言</h3>
 * <tyDecl>     ::= type <ctorHead> <tyVar>* = |? <ctorImpl> (| <ctorImpl>)*
 *
 * <ctorImpl>   ::= <ctorHead> <ctorArg>*
 *
 * <valDecl>    ::= <tyAnno>? <lcid> <pattern>* = <exp>
 *
 * <tyAnno>     ::= <lcid> : <type>
 *
 * <h3>式</h3>
 * <exp>        ::= <aExp>+
 *                | \ <pattern>+ -> <exp>
 *                | let <valDecl>+ in <exp>
 *                | if <exp> then <exp> else <exp>
 *                | case <exp> of (<pattern> -> <exp>)+
 *
 * <aExp>       ::= ( <exp> )
 *                | <aLiteral>
 *                | <ctorHead>
 *                | <lcid>
 *
 * <h3>パターン</h3>
 * <pattern>    ::= <ctorHead> <aPattern>*
 *                | <aPattern>
 *
 * <aPattern>   ::= ( <pattern> )
 *                | <lcid>
 *
 * <h3>型注釈</h3>
 * <type>       ::= <funTyArg> (-> <funTyArg>)*
 *
 * <funTyArg>   ::= ()
 *                | <tyVar>
 *                | <ctorAnno>
 *                | ( <type> )
 *
 * <ctorAnno>   ::= <ctorHead> <ctorArg>*
 *
 * <ctorArg>    ::= ()
 *                | <tyVar>
 *                | <ctorHead>
 *                | ( <type> )
 *
 * <ctorHead>   ::= <ucid>
 *
 * <tyVar>      ::= <lcid>
 * }</pre>
 */
public final class Parser {

	public static Module parse(Tokenized src) {
		Module result = module.parse(src);
		if(result == null) {
			todo("error");
		}
		return result;
	}

	public static Module parse(String fileName, String src) {
		return parse(new Lexer(fileName, src).lex());
	}

	public static Module parse(String fileName) throws IOException {
		return parse(new Lexer(fileName).lex());
	}

	public static AnType parseTypeForTest(String src) {
		Tokenized tokens = new Lexer("TypeForTest.zlk", src).lex();
		SAMENT.parse(tokens);
		return type.parse(tokens);
	}

	// static fieldを上から初期化する都合で補助関数が最上部，全体のパーサは最下部にある

	// TODO: blockを利用してエラー復帰機構
	static <T> Peg<T> block(Peg<T> p) {
		return sequence(ENDENT, SAMENT, p, DEDENT, (_, _, r, _) -> r);
	}

	static <T> Peg<T> mayBlock(Peg<T> p) {
		return choice(block(p), p);
	}

	/**
	 * 複数要素からなるブロックのパーサを返す
	 * @param <T>
	 * @param head 最初の要素のパーサ（頭カンマなしとかそういう用途）
	 * @param head 以降の要素のパーサ
	 * @return
	 */
	static <T> Peg<List<T>> blockPlus(Peg<T> head, Peg<T> tail) {
		Peg<T> samentHead = sequence(SAMENT, head, (_, r) -> r);
		Peg<T> samentTail = sequence(SAMENT, tail, (_, r) -> r);
		return sequence(ENDENT, samentHead, star(samentTail), DEDENT, (_, hd, tl, _) -> prepend(hd, tl));
	}
	static <T> Peg<List<T>> blockPlus(Peg<T> head) {
		return blockPlus(head, head);
	}

	static <T> Peg<List<T>> join(Peg<T> p, Peg<?> delimiter) {
		return sequence(
				p, star(sequence(delimiter, p, (_, v) -> v)),
				(hd, tl) -> prepend(hd, tl));
	}

	static <T> List<T> prepend(T head, List<? extends T> tail) {
		List<T> out = new ArrayList<>(tail.size() + 1);
		out.add(head);
		out.addAll(tail);
		return out;
	}

	// Locationの範囲を求める
	static Location locRange(LocationHolder from, LocationHolder to) {
		return Location.range(from.loc(), to.loc());
	}
	static Location locRange(LocationHolder from, List<? extends LocationHolder> to) {
		if(to.isEmpty()) {
			return from.loc();
		}
		return Location.range(from.loc(), to.getLast().loc());
	}
	static Location locRange(List<? extends LocationHolder> from, LocationHolder to) {
		if(from.isEmpty()) {
			return to.loc();
		}
		return Location.range(from.getFirst().loc(), to.loc());
	}
	static Location locRange(List<? extends LocationHolder> from, List<? extends LocationHolder> to) {
		if(from.isEmpty()) {
			if(to.isEmpty()) {
				return Location.noLocation();
			}
			return Location.range(to.getFirst().loc(), to.getLast().loc());
		}
		return locRange(from.getFirst(), to);
	}

	static final Peg<Token> ENDENT = kind(Kind.ENDENT);
	static final Peg<Token> DEDENT = kind(Kind.DEDENT);
	static final Peg<Token> SAMENT = kind(Kind.SAMENT);

	static final Peg<Token> ARROW = kind(Kind.ARROW);
	static final Peg<Token> BAR = kind(Kind.BAR);
	static final Peg<Token> CASE = kind(Kind.CASE);
	static final Peg<Token> COLON = kind(Kind.COLON);
	static final Peg<Token> ELSE = kind(Kind.ELSE);
	static final Peg<Token> EQUAL = kind(Kind.EQUAL);
	static final Peg<Token> IF = kind(Kind.IF);
	static final Peg<Token> IN = kind(Kind.IN);
	static final Peg<Token> LAMBDA = kind(Kind.LAMBDA);
	static final Peg<Token> LET = kind(Kind.LET);
	static final Peg<Token> LPAREN = kind(Kind.LPAREN);
	static final Peg<Token> MODULE = kind(Kind.MODULE);
	static final Peg<Token> OF = kind(Kind.OF);
	static final Peg<Token> RPAREN = kind(Kind.RPAREN);
	static final Peg<Token> THEN = kind(Kind.THEN);
	static final Peg<Token> TYPE = kind(Kind.TYPE);

	static final Peg<Token> FALSE = kind(Kind.FALSE);
	static final Peg<Token> TRUE = kind(Kind.TRUE);

	static final Peg<Token> UCID = kind(Kind.UCID);
	static final Peg<Token> LCID = kind(Kind.LCID);
	static final Peg<Token> DIGITS = kind(Kind.DIGITS);

	/* 型注釈
	 * <tyVar>      ::= <lcid>
	 *
	 * <ctorHead>   ::= <ucid>
	 *
	 * <parenType>  ::= ( <type> )
	 *
	 * <ctorArg>    ::= ()
	 *                | <tyVar>
	 *                | <ctorHead>
	 *                | <parenType>
	 *
	 * <ctorAnno>   ::= <ctorHead> <ctorArg>*
	 *
	 * <funTyArg>   ::= ()
	 *                | <tyVar>
	 *                | <ctorAnno>
	 *                | <parenType>
	 *
	 * <type>       ::= <funTyArg> (-> <funTyArg>)*
	 */
	static final Peg<AnType.Var> tyVar =
			LCID.map(token -> new AnType.Var(token.str(), token.loc()));

	static final Peg<Token> ctorHead = UCID;

	static final Peg<AnType> parenType = sequence(
			LPAREN, lazy(() -> type()), RPAREN,
			(s, ty, e) -> ty.updateLoc(locRange(s, e)));

	static final Peg<AnType> ctorArg = choice(  // コンストラクタの引数部分に来れる要素
			tyVar,
			ctorHead.map(t -> new AnType.Type(t.str(), List.of(), t.loc())),
			parenType);

	static final Peg<AnType.Type> ctorAnno = sequence(
			ctorHead, star(ctorArg),
			(h, args) -> new AnType.Type(h.str(), args, locRange(h, args)));

	static final Peg<AnType> funTyArg = choice(  // 関数型の引数部分に来れる要素
			tyVar,
			ctorAnno,
			parenType);

	static final Peg<AnType> type = sequence(funTyArg, star(sequence(ARROW, funTyArg, (_, t) -> t)),
			(hd, tl) -> {
				if(tl.size() == 0) {
					return hd;
				}
				AnType result = tl.removeLast();
				for(AnType ty : tl.reversed()) {
					result = new AnType.Arrow(ty, result, locRange(ty, result));
				}
				return new AnType.Arrow(hd, result, locRange(hd, result));
			});

	private static Peg<AnType> type() {
		return type;
	}

	/* パターン
	 * <aPattern>   ::= <lcid>
	 *                | ( <pattern> )
	 *
	 * <pattern>    ::= <ctorHead> <aPattern>*
	 *                | <aPattern>
	 */
	static final Peg<Pattern> aPattern = choice(
			LCID.map(t -> new Pattern.Var(t.str(), t.loc())),
			sequence(LPAREN, lazy(() -> pattern()), RPAREN,
					(s, pat, e) -> pat.updateLoc(locRange(s, e))));

	static final Peg<Pattern> pattern = choice(
			sequence(ctorHead, star(aPattern),
					(h, args) -> new Pattern.Ctor(h.str(), args, locRange(h, args))),
			aPattern);

	private static Peg<Pattern> pattern() {
		return pattern;
	}

	/* 式
	 * <literal>    ::= <digits>
	 *                | True
	 *                | False
	 *
	 * <aExp>       ::= <literal>
	 *                | <lcid>
	 *                | <ctorHead>
	 *                | ( <exp> )
	 *
	 * <appExp>     ::= <aExp>+
	 *
	 * <lambdaExp>  ::= \ <pattern>+ -> <exp>
	 *
	 * <letExp>     ::= let <funDecl>+ in <exp>
	 *
	 * <ifExp>      ::= if <exp> then <exp> else <exp>
	 *
	 * <caseExp>    ::= case <exp> of (<pattern> -> <exp>)+
	 *
	 * <exp>        ::= <varExp>
	 *                | <appExp>
	 *                | <lambdaExp>
	 *                | <letExp>
	 *                | <ifExp>
	 *                | <caseExp>
	 */
	static final Peg<Exp> exp_ = lazy(() -> exp());

	static final Peg<Exp.Cnst> literal = choice(
			DIGITS.map(t -> new Exp.Cnst(Integer.parseInt(t.str()), t.loc())),
			TRUE.map(t -> new Exp.Cnst(true, t.loc())),
			FALSE.map(t -> new Exp.Cnst(false, t.loc())));

	static final Peg<Exp> aExp = choice(
			literal,
			LCID.map(t -> new Exp.Var(t.str(), t.loc())),
			ctorHead.map(t -> new Exp.Var(t.str(), t.loc())),
			sequence(
					LPAREN, exp_, RPAREN,
					(s, exp, e) -> exp.updateLoc(locRange(s, e))));

	static final Peg<Exp> appExp =
			plus(aExp).map(l -> l.size() == 1 ? l.getFirst() : new Exp.App(l, locRange(l, l)));

	static final Peg<Exp.Lamb> lambdaExp = sequence(
			LAMBDA, plus(pattern), ARROW, mayBlock(exp_),
			(s, pat, _, e) -> new Exp.Lamb(pat, e, locRange(s, e)));

	static final Peg<Exp.Let> letExp = sequence(
			LET, blockPlus(lazy(() -> valDecl())), SAMENT, IN, mayBlock(exp_),
			(s, decls, _, _, e) -> new Exp.Let(decls, e, locRange(s, e)));

	static final Peg<Exp.If> ifExp = choice(
			sequence(IF, exp_, THEN, exp_, ELSE, exp_,  // one-line style
					(s, c, _, e1, _, e2) -> new Exp.If(c, e1, e2, locRange(s, e2))),
			sequence(IF, exp_, THEN, block(exp_), SAMENT, ELSE, block(exp_),  // two-block style
					(s, c, _, e1, _, _, e2) -> new Exp.If(c, e1, e2, locRange(s, e2))));

	static final Peg<CaseBranch> caseBranch = sequence(
			pattern, ARROW, mayBlock(exp_),
			(pat, _, e) -> new CaseBranch(pat, e, locRange(pat, e)));

	static final Peg<Exp.Case> caseExp = sequence(
			CASE, exp_, OF, blockPlus(caseBranch),
			(s, c, _, cases) -> new Exp.Case(c, cases, locRange(s, cases)));

	static final Peg<Exp> exp = choice(
			appExp,
			lambdaExp,
			letExp,
			ifExp,
			caseExp);

	private static Peg<Exp> exp() {
		return exp;
	}

	/* 宣言
	 * <ctorImpl>   ::= <ctorHead> <ctorArg>*
	 *
	 * <variants>   ::= <ctorImpl>
	 *                | (| <ctorImpl>)+
	 *
	 * <tyDecl>     ::= type <ctorHead> <tyVar>* = (| <ctorImpl>)+
	 *
	 * <tyAnno>     ::= <lcid> : <type>
	 *
	 * <valDecl>    ::= <tyAnno>? <lcid> <pattern>* = <exp>
	 *
	 * <topDecl>    ::= <tyDecl>
	 *                | <valDecl>
	 */
	static final Peg<Constructor> ctorImpl = sequence(
			UCID, star(ctorArg),
			(name, args) -> new Constructor(name.str(), args, locRange(name, args)));

	static final Peg<List<Constructor>> variants = choice(
			join(ctorImpl, BAR),  // one-line style
			blockPlus(sequence(BAR, ctorImpl, (_, v) -> v))); // multi-line style

	static final Peg<Decl> tyDecl = sequence(
			TYPE, UCID, star(tyVar), EQUAL, variants,
			(s, name, vars, _, ctors) -> new TypeDecl(name.str(), vars, ctors, locRange(s, ctors)));

	private record NameAndTyAnno(String name, AnType ty, Location loc) implements LocationHolder {};

	static final Peg<NameAndTyAnno> tyAnno = sequence(
			LCID, COLON, type, SAMENT,
			(name, _, ty, _) -> new NameAndTyAnno(name.str(), ty, locRange(name, ty)));

	static final Peg<Decl.ValDecl> valDecl = sequence(
			optional(tyAnno), LCID, star(pattern), EQUAL, mayBlock(exp),
			(maybe, name, args, _, e) -> {
				if(maybe.isEmpty()) {
					return new Decl.ValDecl(name.str(), Optional.empty(), args, e, locRange(name, e));
				}
				NameAndTyAnno anno = maybe.get();
				if(!anno.name.equals(name.str())) {
					todo("type annotaion name missmatch");
				}
				return new Decl.ValDecl(name.str(), Optional.of(anno.ty), args, e, locRange(anno, e));
			});

	private static Peg<Decl.ValDecl> valDecl() {
		return valDecl;
	}

	// コンパイル単位
	static final Peg<String> headLine = sequence(SAMENT, MODULE, UCID, (_, _, name) -> name.str());

	static final Peg<Decl> topDecl = choice(
			tyDecl,
			valDecl);

	static final Peg<Module> module = sequence(
			sequence(SAMENT, MODULE, UCID, (_, _, name) -> name.str()),
			star(sequence(SAMENT, topDecl, (_, d) -> d)),
			(name, decls) -> new Module(name, decls));
}
