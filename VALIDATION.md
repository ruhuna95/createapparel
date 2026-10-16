# 自動検証

Minecraft 1.20.1 / Forge 47.4.10 / Java 17を使用する。PowerShellでは事前にJava 17の`JAVA_HOME`を設定する。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\scripts\validate.ps1
```

依存関係がキャッシュ済みなら`.\scripts\validate.ps1 -Offline`を使用できる。
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
