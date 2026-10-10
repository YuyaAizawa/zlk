# 最適化ベンチマーク

公開Driverによる生成コードをJMH 1.37で測定する，JDK 23専用のbenchmark profile．コンパイラ・IR・runtimeへの測定機構追加なし．JUnitは起動だけを担い，JMHが別JVMでwarmup，計時，GC profilingを行う．短いsmokeの数値は高速化の根拠にしない．

## ビルドと入口

以下はWindows bash用．他OSではWrapper名を`./mvnw`，classpath separatorを`:`へ変更する．

```sh
./mvnw.cmd -Pbenchmark test
./mvnw.cmd -Pbenchmark -DskipTests test-compile
./mvnw.cmd -Pbenchmark dependency:build-classpath -Dmdep.outputFile=target/benchmark-classpath.txt
IFS= read -r dependencies < target/benchmark-classpath.txt || test -n "$dependencies"
CP="target/test-classes;target/classes;$dependencies"

# 継承したJUnit入口を1題材だけ明示選択する，fork 1のsmoke
./mvnw.cmd -Pbenchmark -Dtest=zlk.benchmark.OperandStackBenchmark -Dzlk.benchmark.smoke=true test

# 全題材・全sourceCase・全optionCaseのsmoke．JITと-Xintを別保存する
java -cp "$CP" zlk.benchmark.BenchmarkMain smoke all target/benchmarks/jit
java -cp "$CP" zlk.benchmark.BenchmarkMain smoke all target/benchmarks/xint -Xint

# 本測定は1題材ずつ実行する
java -cp "$CP" zlk.benchmark.BenchmarkMain run OperandStackBenchmark target/benchmarks/jit
java -cp "$CP" zlk.benchmark.BenchmarkMain run OperandStackBenchmark target/benchmarks/xint -Xint
```

通常の`-Pbenchmark test`は契約テストだけを選択し，`*Benchmark`の長時間実測を起動しない．契約テストには2件の短いfork試験を含む．実測は明示的な`-Dtest`，CLIまたはEclipseのJUnit実行で起動する．JUnit入口は既定で本測定，`zlk.benchmark.smoke=true`でsmokeを選択する．CLIの定義指定は単純名または完全修飾名，`all`だけが組込み一覧を使用する．追加定義の単独起動には一覧への登録不要．

JUnitと定義はtest source setにあるため，**test-classesを含むclasspathが必須**．旧`java -jar target/benchmarks.jar <regex>`は廃止し，shade jarは作らない．通常buildへの切替前には結果を退避し，`clean`を実行する．

## EclipseからのJUnit実行

1. Mavenのprofile選択で`benchmark`を有効化し，Maven Project Updateの実施．
2. `src/benchmark/java`と`src/benchmark-test/java`の追加，JDK 23，method parameter名保存（Mavenの`-parameters`設定）の確認．
3. 題材定義クラスを開き，Run As → JUnit Testで継承した`benchmark()`の実行．
4. smokeの場合はRun ConfigurationのVM argumentsへ`-Dzlk.benchmark.smoke=true`を追加．

JUnit Platformによる継承入口と実forkの動作は契約テストで検査する．EclipseのGUI操作とm2eによるparameter設定反映は，利用環境で別途確認する．

## 1ファイルの定義

定義は`src/benchmark-test/java`へ配置する．生成関数にJUnitやJMH APIは不要．

```java
package zlk.benchmark;

import zlk.compiler.CompilationOptions;

@ZlkBenchmark(shape = InvocationShape.INT_TO_INT)
public class AddBenchmark extends ZlkBenchmarkTest {
    @Source
    public static String source(@Values({"1", "8"}) int amount) {
        return "module Bench\nbench x = add x " + amount + "\n";
    }
    @Options
    public static CompilationOptions options(@Values({"false", "true"}) boolean stack) {
        return CompilationOptions.DEFAULT.set(CompilationOptions.Key.OPT_USE_OPERAND_STACK, stack);
    }
    public static void verify(CompiledBenchmark compiled, int amount) throws Throwable {
        for(Integer input : new Integer[] {17, -31, Integer.MAX_VALUE}) {
            Integer actual = (Integer) compiled.handle().invokeExact(input);
            if(actual.intValue() != input + amount) throw new IllegalStateException("unexpected result");
        }
    }
}
```

- `@ZlkBenchmark`：shape必須．既定AverageTime，ns/op，thread 1，fork 3，warmup／measurement各5回・各1秒，GC有効．限定overrideは`forks`，`warmupIterations`，`measurementIterations`，`seconds`，`gc`．forkと反復・時間は正値のみ．
- `@Source`：public static String生成関数を正確に1個．
- `@Options`：public static CompilationOptions生成関数を0個または1個．省略時はDEFAULTそのもの．
- `@Values`：全引数へ付与．int／booleanのみ，任意個の引数の外積．非空，型変換後の重複なし，booleanは`false`／`true`だけ．
- `verify`：public static voidで必須．先頭引数CompiledBenchmark，後続はSourceと同じ型・宣言順．Trial setupと静的reportでABI検査後に呼び出す．
- case：引数宣言順・Values記載順の決定的列挙．例`fields=8;updates=32`，無引数は`fixed`．順序変更，未知の値・名前，余分な引数を拒否．parameter名情報がなければ定義エラー．
- shape：`INT_TO_INT`は`(Integer)Integer`，`INT_TO_RECORD`は`(Integer)ZlkRecord`．INTはprimitiveではなく生成ABIのboxed Integer．

生成関数は同じ引数に対して決定的にする．同一sourceCaseのsource hashはoptionCase間で一致を検査する．乱数・時刻・環境・可変static状態へ依存しない．public static reflectionは定義の解釈境界に限り，private access／setAccessibleと測定区間でのreflectionは禁止．各forkは定義を再読込し，launcherの生成済みsourceやstatic mapに依存しない．

## 組込み題材

| 題材 | SourceのValues | Optionsの比較 |
|---|---|---|
| RecordSingleFieldBenchmark | fields=2,8,32，updates=1,8,32 | reuse=false,true |
| RecordMultiFieldBenchmark | fields=8,32，updates=1,8,32 | reuse=false,true |
| RecordSharedBenchmark | fields=2,8,32 | reuse=false,true |
| OperandStackBenchmark | depth=8,32,128 | stack=false,true |
| NamedLocalBenchmark | fixed | stack=false,true |
| CombinedBenchmark | fields=8,32，updates=8,32 | reuse=false,true × stack=false,true |

全60case．対象keyだけをDEFAULTから変更し，非対象keyはDEFAULTのまま保持する．全実効keyを結果へ保存する．`OptimizationMode`とsource parameterの別一覧は廃止した．RecordSharedは共有recordの不発火，NamedLocalはnamed binderのlocal命令不変性を確認するnegative control．MultiField／Combinedは1式で4fieldを更新し，runtime更新呼出し数はupdatesの4倍．

Trial setupはcompile，完全なbytecode集合を持つparent-first loader，typed MethodHandle，ABI検査，独立期待値検査を担う．測定本体は事前生成した`Integer[] {17,257,-31,1009}`の巡回，typed invokeExact 1回，結果返却だけ．Record題材は最終recordを返しescapeを伴う．Sharedは結果recordも追加割当するため，正例と絶対割当量を直接比較しない．1 opは関数1呼出しであり，更新1回ではない．

## 結果と静的report

実測は`<output-root>/<定義単純名>/<UUID>/results.json`と`metadata.json`へ保存する．JUnitのoutput-rootは`target/benchmarks`．metadataはcase，UTF-8 source hash，全実効options，shape，smoke区別，実際の測定設定，JVM args，JDK／VM／JMH versionを保存する．別途revision／dirty状態，CPU，OSとコマンド全文も保存する．

```sh
java -cp "$CP" zlk.benchmark.BenchmarkMain report all target/benchmarks/report
# 実行ごとに表示されるCSVの出力先でUUIDを確認する
# 任意の追加定義をmain側reportへ直接渡すことも可能
java -cp "$CP" zlk.benchmark.BytecodeReport target/benchmarks/custom.csv target/benchmarks/custom-classes zlk.benchmark.AddBenchmark
javap -v -c -p 'target/benchmarks/report/<UUID>/classes/case-0/Bench.class'
```

CLIの`report`は毎回`<output-root>/<UUID>/`を新規作成し，`bytecode.csv`と`classes/`をまとめて保存する．既存reportは上書きしない．出力先はコンソールへ表示する．`BytecodeReport`の直接起動と`write` APIは明示指定したpathへの出力であり，既存ファイルを上書きするため，履歴保存には共通CLIを使用する．

CSVのjoin keyは`definition_class`／`source_case`／`option_case`．JMH JSONのParams `definitionClass`／`sourceCase`／`optionCase`と対応する．CSVは全実効options，source_sha256，module／method／descriptor，全class／module byte数，class数，正確なCode.code_length，ALOAD／ASTORE，max_locals／max_stack，immutable／in-place更新呼出し数を記録する．呼出しはopcode，owner，name，descriptor，interface flagまで照合する．不正class／不在method／不在Codeは失敗とする．

sourceと全classのdumpはCSV行順の`classes/case-<0始まりindex>/`へ保存し，人間向けcaseは`case.json`へ保存する．旧mode列のCSVは旧revisionの成果物であり，新CSVに偽装変換しない．

GC profilerの`gc.alloc.rate.norm`はB/opであり，正確なallocation回数/opではない．JITやescape analysisはlocal操作や割当を除去できる．boxingとMethodHandle dispatchのoverheadも残り，bytecode改善は時間差を保証しない．通常JITを主結果，-Xintやescape analysis無効化を別条件として保存する．性能値は合否条件にしない．

## 通常buildへの復帰

```sh
# target/benchmarksなどをtarget外へ退避した後
./mvnw.cmd clean test
./mvnw.cmd dependency:tree
# JMH依存，benchmark class，BenchmarkList，専用testがないことを確認
git diff --check
```
