# 自動検証

## 配置と実行区分

- `src/test/java/`：JUnitテスト。
- `src/test/gametest/java/`：共通GameTestとFixture（`gameTest` source set）。
- `src/test/resources/`：JUnitとGameTestの検証用リソース。
- `src/test/jei/java/`：専用のJEIあり／なしClient・Server起動検証（`jeiTest` source set）。
- `src/test/recipe/java/`：任意レシピ・銀MODの専用Server起動検証（`recipeTest` source set）。

専用source setは対応する検証起動にだけ追加し、通常起動と配布JARには含めない。
共通の`gameTest`は`runGameTestServer`と、それを継承するレシピ／JEIの検証Serverで読み込む。
通常の`runClient`／`runServer`には読み込まないため、そこで既存GameTestを`/test`から実行する構成ではない。
以下のコマンドはすべてプロジェクトルートで実行する。

## 一括検証

Minecraft 1.20.1 / Forge 47.4.10 / Java 17を使用する。PowerShellでは事前にJava 17の`JAVA_HOME`を設定する。
化学連携APIのコンパイル用に、後述のDestroy・Petrolparkの指定2 JARを`run/phase25/dependencies/`へ配置する。
これらは`compileOnly`の開発依存で、通常Client／Serverのruntimeや配布JARには含めない。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradle\validate.ps1
```

依存関係がキャッシュ済みなら`.\gradle\validate.ps1 -Offline`を使用できる。
スクリプトはプロジェクトルートでGradle Wrapperを次の順に実行し、失敗した段階で停止する。
JEIなしのDedicated Serverを標準条件とする。

| 順序 | コマンド | 確認する内容 |
| --- | --- | --- |
| 1 | `.\gradlew.bat -PjeiEnabled=false runData` | Recipe・Tag・Model・Language等の生成 |
| 2 | `.\gradlew.bat -PjeiEnabled=false build` | 全JUnit、コンパイル、配布JAR生成 |
| 3 | `.\gradlew.bat -PjeiEnabled=false runGameTestServer` | 全GameTest、実Registry・Tag・Recipe・Preset |

各段階を別のGradle呼び出しにして、Datagenで更新したファイルを後続の`processResources`にも反映する。
JUnitは`build/reports/tests/test/index.html`、GameTestのログは最新の`run/gametest/<実行ID>/logs/latest.log`を確認する。
GameTestは実行ごとに新しい`run/gametest/<実行ID>/`を使用し、過去のWorldを再利用しない。テスト構造物はGradleがコピーする。

## 対象を絞る場合

```powershell
.\gradlew.bat test --tests '*ResourceIntegrityTest' --tests '*GarmentResourceValidationTest'
```

`ResourceIntegrityTest`は手書き／生成リソースのJSON、PNG、重複パス、ローカルModel／Texture参照、Item Tagの循環とRecipeのTag参照を確認する。
`GarmentResourceValidationTest`は全Garment Definition、全Slot／Style／Body Profile、Template／Part／Texture Variant、未使用Variantを含む画像規則と型紙の色Channelを確認する。
`DataIntegrityGameTests`は実Registry上で238 Fabricと属性／Tagの一致、Recipe参照、全Sewing Recipe・Designのassemble、Citizen Presetと被覆用Tagを確認する。

外部名前空間のModel／Textureの実在や実際の描画、GUI、Multiplayerはこの静的検査の保証範囲に含まれない。
Client関連の変更では追加で`runClient`、JEI関連では`runJeiTestClient`／`-PjeiEnabled=false runJeiTestClient`／`-PjeiEnabled=false runJeiTestServer`を使用する。
GameTestやJUnitは外部Datapack全般の妥当性を保証するものではなく、同梱データの回帰検証を目的とする。

## 既存テストの棚卸し

Phase24着手時点はJUnit 116クラス・777件、GameTest 27クラス・217メソッド。
777件はPhase23最終buildの実行件数で、パラメーター化テストを含む。GameTestの217はソース上の登録対象数であり、Phase23で実行した限定18件と区別する。
Phase24完了時（2026-10-16）はJUnit787件・Dedicated GameTest221件がすべて成功し、統一スクリプトの全段階を通過した。

| 分野 | 既存の主な検証 | Phase24の追加範囲 |
| --- | --- | --- |
| Fabric／染料／Recipe | `AdditionalDyeTest`、`GarmentFabricRecipeTest`、`SurvivalRecipeTest`、追加染料・機械・JEI表示GameTest | 全RecipeのItem／Fluid／Tag参照、238 Fabricの属性・Tag集合一致 |
| Garment／描画 | 衣類別AssetTest、`DesignVariationAssetTest`、PixelOps、Geometry、Bust、Profile、Cache、Item表示 | 全Definition／Template／Partの横断参照、全Style組合せ、全Variantの画像規則 |
| Pattern／装備／GUI | Pattern保存差分、色選択、GarmentData、Style、Apparel Inventory、Wardrobe | 全Sewing Recipe・DesignのPattern保存／assemble、必要Channelと参照の照合 |
| Citizen | Presetの厳密lint、NBT／移行、頭髪／Body資産、変換、GUI、行動、Route／Crossing／Scheduler | 実ロード件数との照合、黙って除外されたPresetの検出、被覆TagのGarment／Slot照合 |
| 展示／機械／案内 | Mannequin、Hanger、Rack、Mirror、養蚕台、Ponder／Tooltip、JEIの個別検証 | 全JSON／PNGの形式、生成／手書きの重複、通常Model／BlockStateの参照 |

既存の個別仕様・座標・合成式のテストは維持し、横断検査で置き換えない。
テストの入力は追跡対象のソース・リソース・`src/test/resources/`とし、ローカル専用資料には依存させない。
経路テストの長時間歩行は屋根・壁のある乾いた区画で行い、外部の水流からMarkerを保護する。
Citizen視線テストは構造物内に対象を置き、固定tickではなく実際の旋回完了を待つ。

## Phase25 の任意レシピ検証

### Destroy入りの手動Client起動

```powershell
.\gradlew.bat runDestroyClient
```

下記の依存JARを`run/phase25/dependencies/`へ配置してから実行する。
`-PdestroyEnabled=true`は指定しない（通常runtimeと専用`mods/`の二重ロードを防ぐため、指定時は停止する）。
Gradleが開発環境用にリマップしたDestroy・Petrolpark・The Silver Ageの3 JARを
専用環境`run/phase25/client/mods/`へコピーして起動する。通常の`runClient`の環境や配布JARには追加しない。
JEIは既定で有効で、不要なら`-PjeiEnabled=false runDestroyClient`を使用する。
自動検証用のClient Launcherは含まず、手動操作用として起動したままになる。

鏡のDestroy製法は共通銀Tagを使う硝酸溶解→アンモニア錯体化→HoneyとのMixing。
銀インゴット1個は4.5 mol、最終工程は錯体1 mol/LのMixture 900 mB＋Honey 100 mB→加工液1000 mB。
亜鉛製法を使う場合だけ、Worldの`serverconfig/createapparel-server.toml`で
`forceMirrorCoatingFallback = true`に設定し、Worldを再起動する。

### 自動検証

`RecipeRouteSelectionTest`はDestroy有無×2製品の強制設定の全8組合せを確認する。
`OptionalIntegrationRecipeDataTest`はDatagenの条件・入力・出力を確認する。
`OptionalIntegrationGameTests`は実際のRecipeManager、初回SERVER設定、標準Datapack再読込、
加熱Basinの素材／液体消費、Nylonの通常クラフトを検証する。

```powershell
.\gradlew.bat -PjeiEnabled=false -PrecipeTestCase=A runRecipeTestServer
.\gradlew.bat -PjeiEnabled=false -PrecipeTestCase=B runRecipeTestServer
.\gradlew.bat -PjeiEnabled=false -PdestroyEnabled=true -PrecipeTestCase=C runRecipeTestServer
.\gradlew.bat -PjeiEnabled=false -PdestroyEnabled=true -PrecipeTestCase=D runRecipeTestServer
.\gradlew.bat -PjeiEnabled=false -PdestroyEnabled=true -PrecipeTestCase=E runRecipeTestServer
.\gradlew.bat -PjeiEnabled=false -PdestroyEnabled=true -PrecipeTestCase=F runRecipeTestServer
.\gradlew.bat -PdestroyEnabled=true runJeiTestClient
.\gradlew.bat -PdestroyEnabled=true -PjeiMirrorFallback=true runJeiTestClient
.\gradlew.bat -PjeiEnabled=false -PdestroyEnabled=true runJeiTestClient
.\gradle\validate.ps1 -OptionalRecipes -Destroy
```

Destroyを使うruntime検証では、`run/phase25/dependencies/`へ以下の3 JARを配置する。
`-PoptionalIntegrationModsDir=<directory>`で配置先を変更できる。配布JARの必須依存には追加しない。

- Destroy `destroy-1.20.1-0.1.4.jar`（下記PRの検証版）
- Petrolpark's Library `petrolpark-1.20.1-1.4.24.jar`（同PRの同梱版）
- The Silver Age `thesilverage-forge-1.20.1-1.3.0.jar`

Create6.0.8では公開版Destroy0.1.3が削除済み`TagGen.tagBlockAndItem`呼び出しで起動に失敗した。
検証には[Destroy PR #775](https://github.com/Petrolpark-Mods/Destroy/pull/775)の添付`mods.zip`を使用する。
この0.1.4は公開安定版ではなく、PRの互換修正版である。公開版0.1.3の動作保証と混同しない。
JARはローカルの検証専用であり、Create: Apparelの配布物には含めない。

ケースA/BはDestroyなし、C〜FはDestroyあり。C/EはMirrorをDestroy製法、D/Fは強制Fallbackとする。
`SilverIntegrationGameTests`は実銀Tag、銀種・反応量論・4.5 molの実消費、錯体化、NBT往復、
最終Mixingの実液体消費と濃度制限、空の銀Tag ContextでのRecipe読み込み拒否を確認する。
銀アクセサリー素体は鉄／実The Silver Ageの銀ナゲットでCompactingし、ネックレス3個・ブレスレット2個の消費、
不足時の拒否、銀Tag未導入時にEmpty Tagのバリアを材料として扱わないことも確認する。
JEIの追加検証は、同期された最終Recipeに応じて化学2反応が表示／非表示になることと、
標準Mixingレイアウト・銀原子の表示モデル・Datapack再読込を確認する。
専用Client LauncherはForgeの実ソート済みタブでCreate本体→Create建築→Apparel→衣類の順序も照合する。
`run/phase25/server-<case>/<実行ID>/world/serverconfig/`に検証専用設定を作成する。
`-PrecipeTestRunId=<ID>`で同じ検証Worldを再使用して再起動を確認できる。
JEIなしの`runJeiTestClient`は同一JVMで3つの新規Worldを順に起動し、SERVER設定の
`false/false → true/true → false/false`と停止時の選択クリアも確認する。

鏡化学実装前の2026-10-17の確認では、DestroyなしのDatagen／JUnit792件・build／全Dedicated GameTest226件、
A/B各6件が成功した。実ClientはJEIあり／なし双方の新規World作成とレシピ同期、
JEIの実検索・Datapack再読込が成功した。同一JVMで設定が異なる3Worldへの切替と停止時の選択クリアも成功した。
PR #775のDestroy0.1.4／Petrolpark1.4.24とThe Silver Age1.3.0では、D/F各6件も成功した。
The Silver Ageの実Registry／共通銀Tagと、Fの通常クラフトでNylon1個消費→Fiber2個を確認した。
同じ検証Worldを再使用したFのDedicated再起動でも6件すべて成功した。

鏡化学実装後はDatagen／JUnit793件・build、A〜F各10件が成功した。
硝酸対イオンを含むMixtureで錯体濃度0.9／1.1 mol/Lの受け入れ、範囲外の拒否と、
Ingredientのネットワーク往復後の同じ条件の保持も確認した。
Destroy＋JEIありの実Clientは通常Mirror製法・強制Fallbackの両設定で、化学2反応の
表示／非表示、標準Mixingレイアウト、Ag原子ModelのBakeとDatapack再読込まで成功した。
JEIなしはDestroyあり／なし双方で3 World切替、SERVER設定と同期Recipeの照合、停止時の選択クリアが成功した。
化学製法を有効にしたケースCの同一検証Worldを再使用するDedicated再起動でも10件すべて成功した。
自動化学試験はDestroyの反応エンジンと実Basinを使い、実Vat設備を手動操作する一連の確認は別途行う。

銀ナゲット対応とタブ順序修正後はDatagen／JUnit794件・build、A/C各11件が成功した。
実Clientのソート済みタブ順序はDestroyあり／なしの双方で確認し、JEI検索・Datapack再読込も成功した。
銀素体用タグは鉄とoptionalの`forge:nuggets/silver`を含むため、銀MOD未導入時も空タグ候補のバリアを使用しない。

旧COMMON `recipes.mirror.enableMirrorCoatingFallback`は廃止した。
`<world>/serverconfig/createapparel-server.toml`の`[recipes]`に
`forceSyntheticFiberFallback = false`と`forceMirrorCoatingFallback = false`を既定値として持つ。
旧設定は自動移行しない。Destroy導入時も従来の亜鉛製法を使う場合は新しい
`forceMirrorCoatingFallback = true`を明示設定する。変更後はWorld／Serverを再起動する。
Destroy未導入なら、両設定がfalseでも自動的にFallback製法を使用する。
