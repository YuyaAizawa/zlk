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

| 題材 | SourceのValues | Optionsの比較 | JMH図の横軸／色分け |
|---|---|---|---|
| RecordSingleFieldBenchmark | fields=2,8,32，updates=1,8,32 | reuse=false,true | updates／reuse，fieldsごとに分割 |
| RecordMultiFieldBenchmark | fields=8,32，updates=1,8,32 | reuse=false,true | updates／reuse，fieldsごとに分割 |
| RecordSharedBenchmark | fields=2,8,32 | reuse=false,true | fields／reuse |
| OperandStackBenchmark | depth=8,32,128 | stack=false,true | depth／stack |
| NamedLocalBenchmark | fixed | stack=false,true | stackそのものを横軸として色分け |
| CombinedBenchmark | fields=8,32，updates=8,32 | reuse=false,true × stack=false,true | updates／reuse，fieldsとstackごとに分割 |

全60case．対象keyだけをDEFAULTから変更し，非対象keyはDEFAULTのまま保持する．全実効keyを結果へ保存する．`OptimizationMode`とsource parameterの別一覧は廃止した．RecordSharedは共有recordの不発火，NamedLocalはnamed binderのlocal命令不変性を確認するnegative control．MultiField／Combinedは1式で4fieldを更新し，runtime更新呼出し数はupdatesの4倍．

Trial setupはcompile，完全なbytecode集合を持つparent-first loader，typed MethodHandle，ABI検査，独立期待値検査を担う．測定本体は事前生成した`Integer[] {17,257,-31,1009}`の巡回，typed invokeExact 1回，結果返却だけ．Record題材は最終recordを返しescapeを伴う．Sharedは結果recordも追加割当するため，正例と絶対割当量を直接比較しない．1 opは関数1呼出しであり，更新1回ではない．

## 結果と静的report

実測は`<output-root>/<定義単純名>/<UUID>/results.json`と`metadata.json`へ保存する．JUnitのoutput-rootは`target/benchmarks`．metadataはcase，UTF-8 source hash，全実効options，shape，smoke区別，実際の測定設定，JVM args，JDK／VM／JMH versionを保存する．別途revision／dirty状態，CPU，OSとコマンド全文も保存する．

### 実測の比較グラフ

JUnit／CLIの`run`と`smoke`は，同じdirectoryへ`report.md`も自動保存する．全組込み題材と追加定義が対象であり，`@ReportChart`の指定は不要．JSONと同じJMHの集計結果から生成し，追加の測定やコンパイルは行わない．既存のJSONのみの成果物には遡及して追加しない．

- **実行時間**：AverageTimeのscore（ns/op）
- **割当量**：GC profilerの`gc.alloc.rate.norm`（B/op）．GC無効時は未測定の説明のみ
- **図の構成**：Mermaid `xychart-beta`の棒グラフ．全組込み題材が`@JmhChart`を指定し，横軸と比較対象は上表のとおり．falseを橙，trueを青で表示．未指定の追加定義はsourceCaseごとに分割し，横軸をoptionCaseとする1系列
- **順序**：並列棒の横軸はintの昇順，系列はfalse→true．図の分割条件と未指定定義はcase列挙順．異なる固定条件・run・JVM設定間の混合なし
- **数値表**：JSONと同じscoreと99.9%信頼区間の誤差．非有限値は「未定義」と表示し，非有限・負のscoreを含む図は非出力．0埋めやcase間の平均化なし
- **測定条件**：定義名，smoke区別，JVM argsの併記．smokeには性能判断を避ける警告

実行時間と割当量は小さいほど良いが，棒の差だけで高速化や有意差を判断しない．誤差と測定条件を合わせて確認する．静的reportの`@ReportChart`とは独立した出力であり，そちらの既存の折れ線グラフは変更しない．

並列棒の指定は`@JmhChart(x = "source.updates", series = "option.reuse")`．xはint引数，seriesはfalse／trueの両値を持つboolean引数の修飾名．残りのSource／Options引数は固定条件．Mermaidのlineは数値軸でも点を等間隔に配置するため，1，8，32の不等間隔を表現できず，折れ線にはしない．棒のカテゴリは`1 / false`，`1 / true`，`8 / false`，…の順に配置し，実測値のみからなる1本のbar宣言を使用．`themeCSS`で奇数番の棒を橙，偶数番を青に指定する．0の配置用slotや複数barの重ね描きは使用しない．系列の欠落はエラーとし，未測定値の補完や点の補間は行わない．色の説明は図外へ記載し，Mermaid 11.17以降の図内凡例には依存しない．色の適用にはMermaidの`themeCSS`を反映するrendererが必要だが，反映されない環境でもカテゴリ名と表からfalse／trueを識別可能．

Source側に可変のint引数がないNamedLocalでは`@JmhChart(x = "option.stack")`としてseriesを省略．この場合のxはfalse／trueの両値を持つboolean引数であり，横軸も数値表もfalse→trueの2カテゴリのみ．図のためのダミーSource引数は追加しない．

```sh
java -cp "$CP" zlk.benchmark.BenchmarkMain report all target/benchmarks/report
# 実行ごとに表示されるCSVの出力先でUUIDを確認する
# 任意の追加定義をmain側reportへ直接渡すことも可能
java -cp "$CP" zlk.benchmark.BytecodeReport target/benchmarks/custom.csv target/benchmarks/custom-classes zlk.benchmark.AddBenchmark
javap -v -c -p 'target/benchmarks/report/<UUID>/classes/case-0/Bench.class'
```

CLIの`report`は毎回`<output-root>/<UUID>/`を新規作成し，`bytecode.csv`，`report.md`と`classes/`をまとめて保存する．既存reportは上書きしない．CSVとMarkdownの出力先はコンソールへ表示する．`BytecodeReport`の直接起動と`write` APIは明示指定したCSV pathと，その隣の`report.md`への出力であり，既存ファイルを上書きするため，履歴保存には共通CLIを使用する．同じdirectoryへ異なる名前のCSVを直接出力した場合も，`report.md`は共通となる．

CSVのjoin keyは`definition_class`／`source_case`／`option_case`．JMH JSONのParams `definitionClass`／`sourceCase`／`optionCase`と対応する．CSVは全実効options，source_sha256，module／method／descriptor，全class／module byte数，class数，正確なCode.code_length，ALOAD／ASTORE，max_locals／max_stack，immutable／in-place更新呼出し数を記録する．呼出しはopcode，owner，name，descriptor，interface flagまで照合する．不正class／不在method／不在Codeは失敗とする．

sourceと全classのdumpはCSV行順の`classes/case-<0始まりindex>/`へ保存し，人間向けcaseは`case.json`へ保存する．旧mode列のCSVは旧revisionの成果物であり，新CSVに偽装変換しない．

### Mermaid xychart
+
+`report.md`はGitHubへ貼り付けられるMermaidコードブロックと数値表．CSVと同じコンパイル結果から生成し，追加コンパイルやCSVの再読込は行わない．静的指標のみを対象とし，JMHの実行時間・GC測定値の可視化は含まない．
+
+図の指定は，各ベンチマーク定義クラスの反復可能な`@ReportChart`．題材のパラメータと比較意図を同じファイルへ配置し，report生成側に題材別の分岐を追加しない構成．
+
+```java
+@ZlkBenchmark(shape = InvocationShape.INT_TO_RECORD)
+@ReportChart(x = "source.updates", series = {"option.reuse", "option.stack"},
+    metrics = {ReportChart.Metric.CODE_LENGTH, ReportChart.Metric.ALOAD})
+public class CombinedBenchmark extends ZlkBenchmarkTest {
+    // Source，Options，verifyは通常の定義と同じ．
+}
+```
+
+| 指定 | 意味 |
+|---|---|
+| `x` | 横軸のパラメータ．`source.`／`option.`と生成関数の引数名による修飾名 |
+| `series` | 1図へまとめる系列のパラメータ．複数指定時は値の組合せごとに1本の線．省略時は1系列 |
+| `metrics` | 縦軸の静的指標．各指標につき別図．既定は`CODE_LENGTH` |
+| `plot` | `LINE`（既定）または`BAR`．複数系列の棒は重ね描きになるため拒否 |
+| `namedSeries` | Mermaid 11.17以降の名前付き系列と図内凡例．既定`false` |
+| 上記に含まれないパラメータ | 値の組合せごとに図を分割する固定条件 |
+
+上の例は`fields`ごと・指標ごとに別図とし，各図で`reuse × stack`の4系列を比較する．`series`へ`source.fields`も加えると，全fieldsを同じ図で比較する．別の横軸や系列構成を併記する場合は`@ReportChart`を追加する．未指定の定義はCSVとdumpを引き続き出力し，Markdownには未指定の説明を記載する．
+
+- 横軸：intは数値の昇順，booleanは`false`→`true`．`@Values`の文字列を検査済みの引数値へ正規化．`1, 8, 32`などの不等間隔値もカテゴリとして等間隔に表示
+- 図と系列の順序：case列挙での初出順．系列条件の表記は`series`の指定順
+- 縦軸：0始まり．全値0の場合は0〜1として退化を回避
+- 数値表：図の直下へ系列番号，系列条件，横軸ごとの正確な値．番号はMermaid内の系列宣言順であり，線上の番号ではない
+- 不正な指定：未知の引数名，横軸と系列の重複，空・重複指標，点の重複・欠落をエラーとして拒否．平均化や0埋めなし
+- 同値の線：重なって表示．negative controlの差がない場合も表で全系列を確認可能
+
+GitHub rendererの版差を考慮し，既定は`xychart-beta`と名前なしの`line [...]`／`bar [...]`を使用する．系列条件と値は外側の表で保持する．図内凡例も必要な場合は，GitHub上の`info`図でMermaidの版を確認したうえで`namedSeries = true`を指定する．散布図はxychartで未対応のため出力しない．
+
+構文と対応版は[Mermaid XY Chart](https://mermaid.js.org/syntax/xyChart.html)，GitHubの版確認方法は[Creating diagrams](https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/creating-diagrams)を参照．

GC profilerの`gc.alloc.rate.norm`はB/opであり，正確なallocation回数/opではない．JITやescape analysisはlocal操作や割当を除去できる．boxingとMethodHandle dispatchのoverheadも残り，bytecode改善は時間差を保証しない．通常JITを主結果，-Xintやescape analysis無効化を別条件として保存する．性能値は合否条件にしない．

## 通常buildへの復帰

```sh
# target/benchmarksなどをtarget外へ退避した後
./mvnw.cmd clean test
./mvnw.cmd dependency:tree
# JMH依存，benchmark class，BenchmarkList，専用testがないことを確認
git diff --check
```
