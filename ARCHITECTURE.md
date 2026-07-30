# コンパイラ見取り図

この文書は，ZLKコンパイラの全体構造，コンパイルフェーズ，主要なデータ構造，およびコードを読むための用語と命名の凡例を示す．個々のクラスやアルゴリズムの詳細は，関連するソースコードとコメントに記載されている．

## コンパイルフェーズと中間表現

```mermaid
flowchart TD
    Source(["source code"]) --> LexPhase["LexPhase / Lexer"]
    LexPhase --> Tokens(["Tokenized"])
    Tokens --> ParsePhase["ParsePhase / Parser"]
    ParsePhase --> AST(["ast"])
    AST --> NamePhase["NamePhase / NameEvaluator"]
    NamePhase --> IC(["idcalc"])

    IC --> ReconPhase["ReconPhase / ConstraintExtractor + TypeReconstructor"]
    ReconPhase --> Reconstruction(["Reconstructed"])
    Reconstruction --> Types(["types<br/>IdMap&lt;Type&gt;"])
    Reconstruction --> CaseTypings(["caseTypings<br/>CaseTyping&lt;Type&gt;"])

    IC --> PatternPhase["PatternPhase / PatternChecker"]
    CaseTypings --> PatternPhase
    PatternPhase --> PcErrors(["Seq&lt;PcError&gt;"])

    IC --> ClosurePhase["ClosurePhase / ClosureConverter"]
    Types --> ClosurePhase
    ClosurePhase --> CC(["clcalc"])

    CC --> BytecodePhase["BytecodePhase / BytecodeGenerator"]
    Types --> BytecodePhase
    BytecodePhase --> Class(["JVM bytecode / .class"])
```

`PatternChecker`は，名前解決後の`IcModule`とcase式ごとの解決済み`CaseTyping<Type>`を検査し，冗長なパターンと網羅されていないパターンを`PcError`として報告する．レコードパターンでは，省略されたフィールドを補って完全な積型として検査するため，対象レコードの解決済みの全フィールド型を必要とする．このため，`PatternChecker`は型再構築とcase pattern型の解決後に実行する．

`NameEvaluator`フェーズは，値名前解決と型解決を行う．名前を解決し`Id`に変換するのは`NameEvaluator`が，型変数やaliasを解決して`Type`に変換するのは`TypeResolver`が担う．

`ConstraintExtractor.Result.caseTypings`内の`PatternTyping<RcType>`は，制約と型変数を共有する．`TypeReconstructor`はこの抽出結果全体を受け取り，制約解決に成功した場合だけ，宣言型の`IdMap<Type>`とcase branchの`PatternTyping<Type>`をまとめた`Reconstructed`を返す．未解決のcase pattern型を`PatternChecker`へ渡す経路は持たない．型再構築に失敗した場合は，後続の`PatternChecker`を実行しない．式全体の型対応表は保持しない．

### 主要な中間表現

#### `ast`

構文解析直後の抽象構文木．ソース上の名前と構文を保持する．構文解析後は変更せず，後続フェーズに必要な情報は別のIRまたは対応表として生成する．

#### `idcalc`

`NameEvaluator`による名前解決後のIR．ローカル変数，外部変数，データコンストラクタなどが`Id`によって識別される．`PatternChecker`，`ConstraintExtractor`，`ClosureConverter`の入力となる．

#### `Constraint`

`recon.constraint`に定義された型推論用のIR．主な構成要素は次のとおり．

- `RcType`：単一化および型再構築で使用する型
- `Constraint`：型の等式，スコープ，宣言グループなどの制約
- `Variable`：union-findによって管理される型変数
- `PatternTyping<RcType>`：case patternの構文木と制約上の型を対応付けた木
- `CaseTyping<RcType>`：一つのcase式に含まれるbranch patternの型付き木
- `RcType.Anno`：型注釈をrigidな`RcType`へ変換した結果
- `RcType.Inst`：多相型をfresh flexでインスタンス化した結果

#### `clcalc`

`ClosureConverter`によるクロージャ変換後のIR．自由変数を明示的に扱い，JVMバイトコード生成の入力となる．

### レコード型

ZLKのレコード型は，行多相を持つ構造的型である．重要な設計は次のとおり．

- **RecordとRowの分離**：安定層では`Type.Record`が`Type.Row`を包み，row tailの`Type.RowVar`は値型`Type`を実装しない．制約層の`RcType.RecordN`／`RowN`，flat層の`FlatType.Record1`／`Row1`も同じ境界を保つ．union-find rootはTYPE／ROW kindを持ち，rank，generalize，instantiateの機構を共有する．
- **open rowによる一様な型推論**：アクセス，更新，レコードパターンは`ConstraintExtractor`でopen rowを含む`CEqual`へlowerする．solverはrowのlabel差分をtailへ束縛し，lacks制約で重複labelを防ぐため，個別構文向けの特例を必要としない．
- **宣言全体でのkind解決と透明alias**：`TypeResolver`は前方参照や相互再帰を含む全型宣言からparameter kindを解決する．型aliasはkind確定後に展開され，alias identityやkind情報をbackendへ持ち越さない．
- **後段では既知shapeだけを利用**：`PatternChecker`は解決済みrowのknown fieldsを積型として扱い，unknown tailを暗黙のwildcardとする．runtimeではopen／closedの区別を消去し，全レコード値をpublic `ZlkRecord` interfaceへ統一する．

### `Id`とクラスファイル中の名前

`Id`は，モジュール名を根として構文上のスコープを`.`で連結した完全修飾名である．クラスファイルのメソッド名は，`Id`からモジュール名と直後の`.`を除き，残りの`.`を`$`へ置換して生成する．次の表では，モジュール名を`M`，型名を`T`，コンストラクタ名を`C`，関数名を`f`，変数名を`x`とする．`k`は0から始まるクロージャ変換時のモジュール内通し番号，`n`は1から始まる同一スコープ内のラムダ番号，`i`は0から始まるcase分岐番号またはカリー化段階番号である．

| 対象 | `Id` | クラスファイル中の名前 |
|---|---|---|
| モジュール | `Id`なし | クラス`M` |
| 型`T` | `M.T` | sealed interface `M$T` |
| コンストラクタ`C` | `M.T.C` | record `M$T$C` |
| トップレベル関数`f` | `M.f` | `M`のstaticメソッド`f` |
| 関数`f`の引数または局所変数`x` | `M.f.x` | ローカル変数スロット．名前情報なし |
| `f`内の局所関数`g` | `M.f.g` | 自由変数がなければ`f$g`．クロージャ化されれば`k$f$g` |
| `f`内の第`n`ラムダ | `M.f._lambda<n>` | クロージャ化後のメソッド`k$f$_lambda<n>` |
| case式の第`i`分岐にある変数`x` | `M.f._<i>.x` | ローカル変数スロット．名前情報なし |
| クロージャ変換後の関数 | `M.<k>.<元のモジュール以下のId>` | `<k>$<元の名前を$で連結した名前>` |
| 関数の第`i`カリー化段階 | `<元のId>.$<i>` | `<元のメソッド名>$$<i>` |
| コンストラクタの部分適用用メソッド | `M.T.C` | 必要な場合に`M`へ追加されるsyntheticメソッド`T$C` |
| コンストラクタの第`i`引数 | 専用の`Id`なし | record componentおよびprivateフィールド`val<i>` |
| 型変数 | `Id`ではなく`Type.Var` | 型消去後の`java/lang/Object` |

## パッケージ構成

### メインコード：`src/main/java/zlk`

| パッケージ | 責務 |
|---|---|
| `ast` | 抽象構文木 |
| `parser` | 字句解析と構文解析 |
| `nameeval` | 名前解決 |
| `idcalc` | 名前解決後のIR |
| `patterncheck` | パターンマッチの冗長性および網羅性の検査 |
| `recon` | 型制約の抽出と型再構築 |
| `clconv` | クロージャ変換 |
| `clcalc` | クロージャ変換後のIR |
| `bytecodegen` | JVMバイトコード生成 |
| `runtime` | 生成コードが利用する実行時interfaceと値の文字列化 |
| `common` | `Id`，`Location`，`Type`などの共通データ構造 |
| `core` | 組み込み関数と組み込み値 |
| `util` | コレクション，`Result`，Pretty Printerなどの汎用部品 |

`Main.java`は，フロントエンドが完成するまでに実装した言語機能を確認するための，コンパイルから実行までの動くサンプルである．コンパイラ全体を統括する完成したドライバではない．

## 用語と命名

### 型推論

- **flex**：単一化によって他の型へ束縛できるflexibleな型変数
- **rigid**：型注釈の検査中に他の型へ束縛できないrigidな型変数
- **generalize**：対象スコープで自由な型変数を量化すること
- **instantiate**：量化された多相型をfresh flexへ置き換えて利用可能にすること

### プロジェクト共通の省略形

長い単語には，コード全体で一貫して使用する次の省略形を定めている．

| 完全形 | 省略形 | 例 |
|---|---|---|
| Annotation | `Anno` | `IcValDecl.anno`，`RcType.Anno` |
| Constructor | `Ctor` | `IcCtor`，`IcVarCtor` |
| Instance / Instantiation | `Inst` | `RcType.Inst` |
| Expression | `Exp` | `IcExp`，`CcExp` |
| Pattern | `Pat` | `pat`，`patTy` |
| Constraint | `Con` | `bodyCon`，`headerCons` |
| Declaration | `Decl` | `IcValDecl`，`IcTypeDecl` |
| Calculation | `calc` | `idcalc`，`clcalc` |
| Conversion / Converter | `conv` | `clconv`，`ClosureConverter` |


