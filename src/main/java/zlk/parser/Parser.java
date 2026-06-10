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
import zlk.util.collection.Seq;
import zlk.util.collection.SeqBuffer;


/**
 * 構文解析器．
 *
 * <h2>文法</h2>
 * ※空白文字の記述を決めかねているためその点正確でない
 * ※panicから始まる非終端記号はパースエラー用
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
 * <aPattern>   ::= _
 *                | ( <pattern> )
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
		if(src.hasNext()) {
			System.err.println(src.restSource());
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

	static <T> Peg<T> block(Peg<? extends T> p) {
		return sequence(
				ENDENT,
				SAMENT,
				p,
				DEDENT,
				(_, _, body, _) -> body);
	}

	/**
	 * 与えられたPegのExpをインデントブロックで囲んだものを受理するPegを返す
	 * @param p
	 * @return
	 */
	static Peg<Exp> blockExp(Peg<? extends Exp> p) {
		return block(choice(p, lazy(() -> panicStmtExp())));
	}

	static Peg<Exp> mayBlockExp(Peg<? extends Exp> p) {
		return choice(blockExp(p), p);
	}

	/**
	 * 複数要素からなるブロックのパーサを返す
	 * @param <T>
	 * @param head 最初の要素のパーサ（頭カンマなしとかそういう用途）
	 * @param head 以降の要素のパーサ
	 * @return
	 */
	static <T> Peg<Seq<T>> blockPlus(Peg<T> head, Peg<T> tail) {
		Peg<T> samentHead = sequence(SAMENT, head, (_, r) -> r);
		Peg<T> samentTail = sequence(SAMENT, tail, (_, r) -> r);
		return sequence(ENDENT, samentHead, star(samentTail), DEDENT, (_, hd, tl, _) -> prepend(hd, tl));
	}
	static <T> Peg<Seq<T>> blockPlus(Peg<T> head) {
		return blockPlus(head, head);
	}

	static <T> Peg<Seq<T>> join(Peg<T> p, Peg<?> delimiter) {
		return sequence(
				p, star(sequence(delimiter, p, (_, v) -> v)),
				(hd, tl) -> prepend(hd, tl));
	}

	static <T> Seq<T> prepend(T head, Seq<T> tail) {
		return Seq.concat(Seq.of(head), tail);
	}

	// Locationの範囲を求める
	static Location locRange(LocationHolder from, LocationHolder to) {
		return Location.range(from.loc(), to.loc());
	}
	static Location locRange(LocationHolder from, Seq<? extends LocationHolder> to) {
		if(to.isEmpty()) {
			return from.loc();
		}
		return Location.range(from.loc(), to.last().loc());
	}
	static Location locRange(Seq<? extends LocationHolder> from, LocationHolder to) {
		if(from.isEmpty()) {
			return to.loc();
		}
		return Location.range(from.head().loc(), to.loc());
	}
	static Location locRange(Seq<? extends LocationHolder> from, Seq<? extends LocationHolder> to) {
		if(from.isEmpty()) {
			if(to.isEmpty()) {
				return Location.noLocation();
			}
			return Location.range(to.head().loc(), to.last().loc());
		}
		return locRange(from.head(), to);
	}

	static <T> Seq<T> flatten (Seq<Seq<T>> seqs) {
		SeqBuffer<T> result = new SeqBuffer<>();
		seqs.forEach(result::addAll);
		return result.toSeq();
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
	static final Peg<Token> WILDCARD = kind(Kind.WILDCARD);

	static final Peg<Token> FALSE = kind(Kind.FALSE);
	static final Peg<Token> TRUE = kind(Kind.TRUE);

	static final Peg<Token> UCID = kind(Kind.UCID);
	static final Peg<Token> LCID = kind(Kind.LCID);
	static final Peg<Token> DIGITS = kind(Kind.DIGITS);

	static final Peg<Token> ILL = kind(Kind.ILL);

	static final Peg<Token> BLACK = kind(Kind::isBlack);
	static final Peg<Token> WORD = kind(Kind::isWord);

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
			ctorHead.map(t -> new AnType.Type(t.str(), Seq.of(), t.loc())),
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
				AnType result = tl.last();
				for(AnType ty : tl.dropLast().reversed()) {
					result = new AnType.Arrow(ty, result, locRange(ty, result));
				}
				return new AnType.Arrow(hd, result, locRange(hd, result));
			});

	private static Peg<AnType> type() {
		return type;
	}

	/* パターン
	 * <aPattern>   ::= _
	 *                | <lcid>
	 *                | <ctorHead>
	 *                | ( <pattern> )
	 *
	 * <pattern>    ::= <ctorHead> <aPattern>+
	 *                | <aPattern>
	 */
	static final Peg<Pattern> aPattern = choice(
			WILDCARD.map(t -> new Pattern.Wildcard(t.loc())),
			LCID.map(t -> new Pattern.Var(t.str(), t.loc())),
			ctorHead.map(t -> new Pattern.Ctor(t.str(), Seq.of(), t.loc())),
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
	 * <literal>        ::= <digits>
	 *
	 * <aExp>           ::= <literal>
	 *                    | <lcid>
	 *                    | <ctorHead>
	 *                    | ( <exp> )
	 *                    | <panicAExp>
	 *
	 * <appExp>         ::= <aExp>+
	 *
	 * <lambdaExp>      ::= \ <pattern>+ -> <exp>
	 *
	 * <valDeclOrPanic> ::= <valDecl>
	 *                    | <panicValDecl>
	 *
	 * <letExp>         ::= let <valDecl>+ in <exp>
	 *
	 * <ifExp>          ::= if <exp> then <exp> else <exp>
	 *
	 * <caseExp>        ::= case <exp> of (<pattern> -> <exp>)+
	 *
	 * <errorExp>       ::= <panicBlockPiece>+
	 *
	 * <exp>            ::= <varExp>
	 *                    | <appExp>
	 *                    | <lambdaExp>
	 *                    | <letExp>
	 *                    | <ifExp>
	 *                    | <caseExp>
	 *                    | <panicExp>
	 */
	static final Peg<Exp> exp_ = lazy(() -> exp());

	static final Peg<Exp.Cnst> literal =
			DIGITS.map(t -> new Exp.Cnst(Integer.parseInt(t.str()), t.loc()));

	static final Peg<Exp> panicAExp =  // TODO: 確実に落とせるのはILL位で他は個別の式構文ごとに対応
			ILL.map(t -> new Exp.Err(t.loc()));

	static final Peg<Exp> aExp = choice(
			literal,
			LCID.map(t -> new Exp.Var(t.str(), t.loc())),
			ctorHead.map(t -> new Exp.Var(t.str(), t.loc())),
			sequence(
					LPAREN, exp_, RPAREN,
					(s, exp, e) -> exp.updateLoc(locRange(s, e))),
			panicAExp);

	static final Peg<Exp> appExp =
			plus(aExp).map(l -> l.size() == 1 ? l.head() : new Exp.App(l, locRange(l, l)));

	static final Peg<Exp.Lamb> lambdaExp = sequence(
			LAMBDA, plus(pattern), ARROW, mayBlockExp(exp_),
			(s, pat, _, e) -> new Exp.Lamb(pat, e, locRange(s, e)));

	static final Peg<Exp.Let> letExp = sequence(
			LET, blockPlus(lazy(() -> valDeclOrPanic())), SAMENT, IN, mayBlockExp(exp_),
			(s, decls, _, _, e) -> new Exp.Let(decls, e, locRange(s, e)));

	static final Peg<Exp.If> ifExp = choice(
			sequence(IF, exp_, THEN, exp_, ELSE, exp_,  // one-line style
					(s, c, _, e1, _, e2) -> new Exp.If(c, e1, e2, locRange(s, e2))),
			sequence(IF, exp_, THEN, block(exp_), SAMENT, ELSE, block(exp_),  // two-block style
					(s, c, _, e1, _, _, e2) -> new Exp.If(c, e1, e2, locRange(s, e2))));

	static final Peg<CaseBranch> caseBranch = sequence(
			pattern, ARROW, mayBlockExp(exp_),
			(pat, _, e) -> new CaseBranch(pat, e, locRange(pat, e)));

	static final Peg<Exp.Case> caseExp = sequence(
			CASE, exp_, OF, blockPlus(caseBranch),
			(s, c, _, cases) -> new Exp.Case(c, cases, locRange(s, cases)));

	record Panic(Location loc) implements LocationHolder {}

	static final Peg<Panic> panicLine =
			plus(BLACK)
			.map(tokens -> new Panic(locRange(tokens.head(), tokens.last())));

	static final Peg<Panic> panicStmt = sequence(
			panicLine,
			optional(lazy(() -> panicBlock())),
			(line, maybeBlock) -> new Panic(locRange(line, maybeBlock.orElse(line)))
	);

	static final Peg<Panic> panicBlock =
			block(sequence(
					panicStmt,
					star(sequence(SAMENT, panicStmt, (_, stmt) -> stmt)),
					(hd, tl) -> new Panic(locRange(hd, tl)
			)));
	private static Peg<Panic> panicBlock() {
		return panicBlock;
	}

	static final Peg<Exp.Err> panicStmtExp =
			panicStmt.map(p -> new Exp.Err(p.loc));
	private static Peg<Exp.Err> panicStmtExp() {
		return panicStmtExp;
	}

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
	 *                | <panicTyDecl>
	 *                | <panicValDecl>
	 */
	static final Peg<Constructor> ctorImpl = sequence(
			UCID, star(ctorArg),
			(name, args) -> new Constructor(name.str(), args, locRange(name, args)));

	static final Peg<Seq<Constructor>> variants = choice(
			join(ctorImpl, BAR),  // one-line style
			blockPlus(sequence(BAR, ctorImpl, (_, v) -> v))); // multi-line style

	static final Peg<Decl> tyDecl = sequence(
			TYPE, UCID, star(tyVar), EQUAL, variants,
			(s, name, vars, _, ctors) -> new TypeDecl(name.str(), vars, ctors, locRange(s, ctors)));

	private record NameAndTyAnno(String name, AnType ty, Location loc) implements LocationHolder {};

	static final Peg<NameAndTyAnno> tyAnno = sequence(
			LCID, COLON, type, SAMENT,
			(name, _, ty, _) -> new NameAndTyAnno(name.str(), ty, locRange(name, ty)));

	static final Peg<Decl.Value> valDecl = sequence(
			optional(tyAnno), LCID, star(pattern), EQUAL, mayBlockExp(exp),
			(maybe, name, args, _, e) -> {
				if(maybe.isPresent()) {
					NameAndTyAnno anno = maybe.get();
					if(!anno.name.equals(name.str())) {
						return new Decl.ValErr(locRange(anno, e));
					}
					return new Decl.ValDecl(name.str(), Optional.of(anno.ty), args, e, locRange(anno, e));
				}
				return new Decl.ValDecl(name.str(), Optional.empty(), args, e, locRange(name, e));
			});

	private static final Peg<Pattern> patternOrWord =
			choice(
					pattern,
					WORD.map(token -> new Pattern.Err(token, token.loc()))
			);
	// TODO: tyAnnoエラーの復帰
	static final Peg<Decl.ValErr> panicValDecl = sequence(
			optional(tyAnno), LCID, star(patternOrWord), EQUAL, choice(panicStmt, panicBlock),
			(tyAnno, name, _, _, body) -> new Decl.ValErr(locRange(tyAnno.<LocationHolder>map(x -> x).orElse(name), body)));

	static final Peg<Decl.TypeErr> panicTyDecl = sequence(
			TYPE,
			star(BLACK),
			Peg.optional(blockPlus(BLACK)).map(plus -> plus.orElse(Seq.of())),
			(t, b1, b2) -> {
				Seq<Token> tokens = Seq.concat(Seq.of(t), b1, b2);
				return new Decl.TypeErr(locRange(tokens.head(), tokens.last()));
			});

	static final Peg<Decl.Value> valDeclOrPanic = choice(
			valDecl,
			panicValDecl);

	private static Peg<Decl.Value> valDeclOrPanic() {
		return valDeclOrPanic;
	}

	// コンパイル単位
	static final Peg<String> headLine = sequence(SAMENT, MODULE, UCID, (_, _, name) -> name.str());

	static final Peg<Decl> topDecl = choice(
			tyDecl,
			valDecl,
			panicTyDecl,
			panicValDecl);

	static final Peg<Module> module = sequence(
			sequence(SAMENT, MODULE, UCID, (_, _, name) -> name.str()),
			star(sequence(SAMENT, topDecl, (_, d) -> d)),
			(name, decls) -> new Module(name, decls));
}
