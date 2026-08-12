package zlk.ir.reuse.own;

/**
 * binderが保持する参照に対する所有権．
 *
 * OWNEDはこのbinderが所有参照を1本引き受け，最終的にTAKEまたはDropする責任を持つ．
 * heap objectがunique（reference count == 1）であることまでは意味しない．
 * BORROWEDは所有参照を呼出し元などが保持しており，この関数からDropしてはならない．
 *
 * 現在の初期実装では追跡対象のbinderを原則OWNEDとして扱うが，
 * 将来のborrowed parameter/pattern decomposition view等で区別を導入できるようOwn IR上に明示する．
 */
public enum Ownership {
	OWNED,
	BORROWED;
}
