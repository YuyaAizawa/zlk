package zlk.compiler.phase.nameeval;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import zlk.compiler.id.Id;
import zlk.util.collection.Stack;
import zlk.util.pp.PrettyPrintable;
import zlk.util.pp.PrettyPrinter;

/**
 * 名前評価環境．
 *
 * <p>Id scopeとlexical bindingの可視期間を分離する．
 * <ul>
 *   <li>scope: 名前を割り当てた {@link Id} の親となるスコープ．
 *       module，value declaration，lambda，case branchがそれぞれ一つの scope を持つ．
 *       let は親と同じ scope を共有し，synthetic let segmentを追加しない．</li>
 *   <li>binding frame: 単純名→Id の map．
 *       let は親と同じ scope を持ちつつ一時 binding frame を push し，
 *       宣言群と body をその frame 内で評価し，退出後に binding を破棄する．</li>
 * </ul>
 *
 * <p>同一 scope で割り当てた単純名は scope 寿命中に再利用しない．
 * 新仕様の shadowing／同名再利用は導入しない．必要なら scope 側に割当済み名前を保持する．
 *
 * <p>scope lifetimeはcallback APIで構造化し，正常・例外を問わず必ず退出する．
 * frameのpush／popはこのクラスの内部だけで行う．
 */
public final class Env {
	/** 現在有効な binding frame の stack（内側が top）． */
	private final Stack<Frame> frames;
	private final Map<String, Id> global;

	public Env() {
		this.frames = new Stack<>();
		this.global = new HashMap<>();
	}

	/**
	 * 現在のscopeに{@code simpleName}を名前とするlexical scopeを作り，
	 * そのscope内で{@code body}を評価する．
	 * 作成したscopeのIdは{@code body}の引数として渡す．
	 */
	public <T> T withScope(String simpleName, Function<Id, T> body) {
		Id scopeId = frames.isEmpty()
				? Id.intern(simpleName)
				: Id.intern(frames.peek().scope().id(), simpleName);
		Frame frame = new Frame(new Scope(scopeId), new HashMap<>());
		return withFrame(frame, () -> body.apply(scopeId));
	}

	/**
	 * 現在のscopeにlambda用scopeを作り，そのscope内で{@code body}を評価する．
	 * 作成したscopeのIdは{@code body}の引数として渡す．
	 */
	public <T> T withLambdaScope(Function<Id, T> body) {
		Frame parent = frames.peek();
		String synthetic = "_lambda" + parent.scope().nextLambdaIndex();
		return withScope(synthetic, body);
	}

	public final class BranchScopeProvider<T> {
		private String prefix;
		private AtomicInteger branchIndexCounter;

		private BranchScopeProvider() {
			Frame parent = frames.peek();
			this.prefix = "_case" + parent.scope().nextCaseIndex() + "_";
			branchIndexCounter = new AtomicInteger(1);
		}

		/**
		 * case-branch用scopeを作り，そのscope内で{@code body}を評価する．
		 */
		public T withBranchScope(Supplier<T> body) {
			String synthetic = prefix + branchIndexCounter.getAndIncrement();
			return withScope(synthetic, _ -> body.get());
		}
	}
	/**
	 * 現在のscopeにcase-branch用のscopeを作るための，BranchScopeProviderを返す．
	 */
	public <T, U> T withCase(Function<BranchScopeProvider<U>, T> body) {
		return body.apply(new BranchScopeProvider<>());
	}

	private <T> T withFrame(Frame frame, Supplier<T> body) {
		frames.push(frame);
		try {
			return body.get();
		} finally {
			frames.pop();
		}
	}

	/**
	 * let用のbinding frameを作り，そのscope内で{@code body}を評価する．
	 * scopeは作らない．
	 */
	public <T> T withLetFrame(Supplier<T> body) {
		Frame parent = frames.peek();
		Frame frame = new Frame(parent.scope(), new HashMap<>());
		return withFrame(frame, body);
	}

	/** 現在の binding frame に名前を登録する．scope は現在の frame と同一． */
	public Id register(String name) throws DuplicatedNameException {
		Frame top = frames.peek();
		Id id = Id.intern(top.scope().id(), name);
		return register(name, id);
	}

	/** 指定 Id で名前を登録する．scope は現在の frame と同一とみなす． */
	public Id register(String name, Id id) throws DuplicatedNameException {
		Frame top = frames.peek();
		Id oldId = top.scope().assign(name, id);
		if (oldId != null) {
			throw new DuplicatedNameException(oldId, id);
		}
		top.ids().put(name, id);
		return id;
	}

	/** 大域に Id をその simpleName で登録する． */
	public Id registerGlobal(Id id) throws DuplicatedNameException {
		String name = id.simpleName();
		Id orig = global.putIfAbsent(name, id);
		if (orig != null) {
			throw new DuplicatedNameException(orig, id);
		}
		return id;
	}

	public Id getOrNull(String name) {
		for (Frame frame : frames) {
			Id id = frame.ids().get(name);
			if (id != null) {
				return id;
			}
		}
		return global.get(name);
	}

	public Id get(String name) {
		Id id = getOrNull(name);
		if (id == null) {
			throw new NoSuchElementException(name);
		}
		return id;
	}
}

/** Id scopeの寿命全体で共有する状態． */
final class Scope {
	private final Id id;
	private final Map<String, Id> assignedIds = new HashMap<>();
	private final AtomicInteger lambdaCounter = new AtomicInteger(1);
	private final AtomicInteger caseCounter = new AtomicInteger(1);

	Scope(Id id) {
		this.id = id;
	}

	Id id() {
		return id;
	}

	Id assign(String name, Id id) {
		return assignedIds.putIfAbsent(name, id);
	}

	int nextLambdaIndex() {
		return lambdaCounter.getAndIncrement();
	}

	int nextCaseIndex() {
		return caseCounter.getAndIncrement();
	}
}

/** 一時binding frame．scope状態はlet frameと親frameで共有する． */
record Frame(Scope scope, Map<String, Id> ids) implements PrettyPrintable {

	@Override
	public void mkString(PrettyPrinter pp) {
		pp.append(scope.id()).append(": ").append(PrettyPrintable.oneLine(ids));
	}
}
