# 自動検証

## 配置と実行区分

テスト関連のソース・リソース・専用ビルド設定・実行スクリプト・手順書を`src/test/`配下に集約する。Gradle Wrapper本体は`gradle/wrapper/`を維持する。

- `src/test/validate.ps1`：共通の一括検証スクリプト。
- `src/test/citizen-gauge-tests.gradle`：Citizen Gauge専用のsource set・起動設定・検証タスク。
- `src/test/java/`：JUnitテスト。
- `src/test/gametest/java/`：共通GameTestとFixture（`gameTest` source set）。
- `src/test/resources/`：JUnitとGameTestの検証用リソース。
- `src/test/jei/java/`：専用のJEIあり／なしClient・Server起動検証（`jeiTest` source set）。
- `src/test/recipe/java/`：任意レシピ・銀MODの専用Server起動検証（`recipeTest` source set）。
- `src/test/route/java/`：Citizen経路の選択実行と斜め移動・短絡安全性の専用GameTest（`routeTest` source set）。
- `src/test/gauge/java/`：Citizen Gauge限定GameTestの選択と専用Client起動検証（`gaugeTest` source set）。

専用source setは対応する検証起動にだけ追加し、通常起動と配布JARには含めない。
共通の`gameTest`は`runGameTestServer`と、それを継承する経路／レシピ／JEIの検証Serverで読み込む。
通常の`runClient`／`runServer`には読み込まないため、そこで既存GameTestを`/test`から実行する構成ではない。
以下のコマンドはすべてプロジェクトルートで実行する。

## 一括検証

Minecraft 1.20.1 / Forge 47.4.10 / Java 17を使用する。PowerShellでは事前にJava 17の`JAVA_HOME`を設定する。
化学連携APIのコンパイル用に、後述のDestroy・Petrolparkの指定2 JARを`run/phase25/dependencies/`へ配置する。
これらは`compileOnly`の開発依存で、通常Client／Serverのruntimeや配布JARには含めない。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\src\test\validate.ps1
```

依存関係がキャッシュ済みなら`.\src\test\validate.ps1 -Offline`を使用できる。
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
標準GameTestと経路専用起動には`src/test/resources/gametest-server.properties`もコピーし、固定seed0・フラット地形・生成構造物なしで実行する。
ForgeのGameTest起動は、設定を省略するとランダムseedの通常地形になる。地下の試験床への自然水流などを試験対象へ混入させない。
通常Client／Dedicated Serverや既存Worldの設定には適用しない。

## 対象を絞る場合

### Citizen Systemの最適化・診断・負荷確認

`CitizenOptimizationTest`／`PopulationRequestQueueTest`はRevision分離、独立Networkの保持、共通64ノード予算、Chain逆引き、予約の診断／回復、Snapshot／Delta、2,000要求の公平性と連続更新時のHeap上限を確認する。`DistrictTerritoryTest`は64 Marker・16 Marker Chunkに加え、幾何変更の探索も256 Chunkセル／tick以内とする。`CitizenScaleTest`はA/B/Cの保存レコードとNBTを比較し、従来の全件ソートと優先キューを同じ2,400試行で比較する。時間にはJIT・GCの変動があるため、機能・件数・予算をassertし、速度比はassertしない。

`CitizenGaugeConsistencyGameTests`はBEとSavedDataの不一致、複製NBTの隔離、実Bearingのcapture／NBT／復元、移動元住所の再利用、Contraption複製と本体破棄、人口だけのBE差分、管理者権限、明示移動による自主Navigation要求の置換を検証する。

追加検証では、予約Dimensionを不変にして局所観測と予約数量を区別し、Conflict／Dimension不一致を通常観測で解消しないことを確認する。Queueは2,500件の大量更新・取消・同時刻再試行、空になった後の古い遠未来Entryの0件収束を確認する。移動中Gaugeは通常BEからのID奪取拒否・ネイティブ再配置・所有NBTが読めない破壊・最終Entityチャンクのロード後回復・旧形式／未ロードの保持を検証する。表示同期は同一Snapshot共有、独立Revision、各32Entryの上限、Level unload時の解放を確認する。

保存定義だけが欠けてもLinkと所属UUIDを再保存後まで保持し、復旧前にOutflowを許可しないこと、重複Gauge行が先行記録を上書きしないこともJUnitで確認する。Consistency GameTestはオフハンドの予約複製を含む数量衝突も検証する。Client QAは表示用アイテムの持替えでも再同期要求の20tick制限を迂回できないことを実Packet数で確認し、診断hoverを`build/gauge-client-diagnostics.png`へ記録する。

中心登録時の1個分離はConsistency GameTestで両手×Survival／Creative、通常スタックへの合流、無関係NBT・元モード維持、満杯時の未登録分Drop、拒否時の無変更、旧スタックの同地点／別地点再記録、設置・地区撤去後に残スタックが予約を持たないことを確認する。Client QAは実USE Packetから8個を1個の記録済みGauge＋7個の未登録Gaugeへ分け、Server予約数量とClientインベントリ同期を照合する。従来の複数個破棄／Craft試験は明示的に旧形式の予約Fixtureを使い、互換性として維持する。

```powershell
.\gradlew.bat -PjeiEnabled=false test --tests '*CitizenOptimizationTest' --tests '*PopulationRequestQueueTest' --tests '*CitizenScaleTest'
.\gradlew.bat -PjeiEnabled=false runGaugeTestServer
.\gradlew.bat -PjeiEnabled=false -PgaugeScale=true runGaugeTestServer
```

最後の専用試験は新規の隔離Worldで、Gauge 200／1,000／2,000、保存Citizen 1,000／5,000／10,000、Marker 2,000／10,000／20,000を順に作る。各規模の実ロードCitizenは100人、実壁パネル16枚、実Marker32個とし、それ以外を未ロード記録として扱う。100tickウォームアップ後に約600tickを測る。`CITIZEN_LIVE_SCALE`へMSPT平均／p95／p99、END maintenance時間、Heap、GC、Network完成tick、Navigation待機、SavedDataのSave／Load／NBTサイズを出力する。MSPTはServer全体、maintenanceはLevel ENDの人口／経路処理であり、Citizen本体tick全体の単独計測ではない。パーセンタイルはこの試験区間のみ。通信・多数Client・全10,000体の同時ロード性能を代用する結果ではない。

単一規模を別JVMで保存・再起動する場合は次を使う。`gaugeScaleCase=1/2/3`が上記の各規模で、run IDを規模ごとに変える。resumeは直前に正常終了した同一Worldを使う。

```powershell
.\gradlew.bat -PjeiEnabled=false -PgaugeScale=true -PgaugeScaleCase=1 -PgaugeTestRunId=scale-a runGaugeTestServer
.\gradlew.bat -PjeiEnabled=false -PgaugeScale=true -PgaugeScaleCase=1 -PgaugeTestRunId=scale-a -PgaugeScaleResume=true runGaugeTestServer
.\gradlew.bat -PjeiEnabled=false -PgaugeMoving=true -PgaugeTestRunId=moving-restart runGaugeTestServer
.\gradlew.bat -PjeiEnabled=false -PgaugeMoving=true -PgaugeTestRunId=moving-restart -PgaugeMovingResume=true runGaugeTestServer
```

`CITIZEN_SCALE_PATH`はPathfinding Schedulerの実行時間、`CITIZEN_GAUGE_VIEW`は同一チャンク／分散した1・4・16人相当のSnapshot／単一編集Deltaのサーバー生成・実Packetエンコード時間とpayload量、`CITIZEN_SCALE_WORLD_SAVE`は全Worldの実Save、`CITIZEN_SCALE_RESTART`は別JVMでのSavedData復元・ID／所属／件数の照合を記録する。Gauge表示は合成ServerPlayerを使い、独立TCP Clientの通信・描画性能ではない。表示上限1024件を維持する。Contraption再起動試験は隔離Worldの離れたforced chunkへネイティブBearing由来の実Entityを保存し、同じEntity UUIDとGauge／District／Link／所属の復元、再配置、Entityが失われた別Gaugeの回復を確認する。VS2 Shipは別機構であり、この互換試験には含まない。

保存Citizen数は人口Registryの件数で、未ロード分の実Entity NBTは作成しない。World Save／再起動も大量Registry＋実Entity100人・実Gauge16枚・実Marker32個が対象となる。全数の実Entity／Blockを配置・保存・ロードしたWorldの性能は未検証として別評価する。

通常の実WorldではOP権限で`/createapparel gauge stats`、`/createapparel gauge profile start`／`stop`を使える。詳細計測は明示開始時だけ有効で、MSPTパーセンタイルは直近4,096tick、平均は計測開始後の全tick。通信カウンタは専用Gauge Info Packetのpayloadで、TCPヘッダーやMinecraft全体のEntity同期は含まない。

未通知のVoid消去の回復は`/createapparel gauge reservations list [page]`／`inspect <UUID>`／`release <UUID>`。現在Dimensionが対象なので他Dimensionは`/execute in <dimension> run ...`を使う。経過時間や未ロードだけでは自動解除しない。`OBSERVED`は所持の完全な証明ではなく、オフライン／未ロード保管品やTrash Cansの復元履歴も考慮して明示解除する。

Trash Cans実MODのQAは`run/gauge-tests/dependencies/`へ公式配布の次の3 JARを置き、`.\gradlew.bat -PjeiEnabled=false -PgaugeTrash=true runGaugeTestServer`を使う。通常のruntime・配布依存には追加しない。

- `trashcans-1.1.1a-forge-mc1.20.4.jar`（配布メタデータおよび実起動で1.20.1対応を確認）
- `supermartijn642corelib-1.1.25a-forge-mc1.20.1.jar`
- `supermartijn642configlib-1.1.8-forge-mc1.20.jar`

Capabilityのsimulate／実投入、空Gauge／記録済み1個・3個、実Hopper、ドロップItemEntity→Hopper、Create Funnelを通して検証する。復元履歴中は予約を維持し、履歴をネイティブAPIで完全削除してからOPコマンドで中心を再利用する。FunnelはSmartBlockEntityの初期化を20tick待ってから実APIを呼ぶ。直接ItemEntityをTrash Canの上へ置くだけで削除する機能はこのMODの入口ではない。

Gaugeの人口・予約・操作・永続化、壁4方向の4パネル設置・個別回収・支持壁撤去時のDropは共通GameTestにも含まれる。GaugeだけのDedicated検証と専用Client検証は次で実行する。

`CitizenPopulationRecoveryGameTests`は平坦な石／ガラス床の成人、下付きハーフ床の子どもを1250tick追跡し、実際に歩き続けてSTUCKEDや瞬間移動を起こさないことも確認する。任意の徘徊先なし／到着済み候補だけの場合は休止し、実経路が閉じ込められた場合は従来の復旧を維持する。再現ログは`build/citizen-wander-stuck-reproduction.log`。

流入／流出GUIは16pxアイコン＋4px間隔＋名称を一組として中央配置する。専用Client QAでは流入／流出の両画面と日英ラベルの実文字幅を確認し、`gauge-client-source.png`／`gauge-client-outflow.png`へ記録する。

単体初期化は実2×2／3×3メニューで、プレビューの副作用なし、1個取得ごとの予約減算、Shift全個クラフト、無関係NBT保持、設置済み地区の保持、流出設定の初期化、空ゲージ／追加材料の不一致を確認する。Forgeの`onPlayerDestroyItem`経路も分割残数・全個消去・既存地区保護を検証する。Client QAはゴミ箱に加え、Creativeアイテム一覧へ右クリックで1個／左クリックで残りを破棄し、サーバーの残数と最終解除を確認する。タブ切替だけでは解除しない。レシピ生成物は`CitizenGaugeAssetTest`で確認する。

`CitizenPopulationRecoveryGameTests`もGauge専用選択と全GameTestへ含む。通常WALK／WANDERの段階STUCKED復旧、FORCE／INDEPENDENTの非テレポート、孤立Marker／STAND／信号待ちの誤判定防止、供給不能な古い要求の背後の実供給、Gateway優先生成と安全Fallbackを検証する。独立batchのConfig短縮は終了時に復元する。`CitizenPopulationTest`では96件の古い供給不能要求と若い要求に対し、再試行20／200tick・4要求／20tickの予算で飢餓しないことも確認する。

`DistrictRouteCapacityTest`は同じ地区内のEdge長の重複なし集計、地区間／片端無所属Edgeの除外、立体／逆向きEdge、Revisionによる無効化、64 Edgeずつの段階処理と部分合計非公開、所属WALK人数の差分更新・保存復元を確認する。Recovery専用GameTestは最終Marker削除後のWANDER復帰、6mに2人の自動切替と定員ちょうどの維持、内部Edge削除後の余剰WALKの降格・保護フラグ、流入時のWALK優先／飽和時WANDER、Gatewayがあっても接続経路を辿る人口移動と到着後の飽和切替、子ども村人の遊び速度相当の属性・実歩行を検証する。子どもの歩行周期は実移動量に追従する1.5倍補正とし、以前の3倍から半速へ調整する。移動速度属性0.3は維持する。

`RouteEdgeCostTest`は地区間の1.5倍費用と、通常分岐で地区内60%／地区間40%となる2択の重みを決定的に検証する。既存Planner GameTestには幾何学的には少し長い地区内経路が、地区間の近道より優先される検査も含める。

`GaugeWirePathTest`は半ブロック格子の迂回、負座標、端点の許可、閉塞時の非描画、4096訪問上限を検証する。専用GameTestは全4壁方向でCitizen／Factory Gaugeの中間パネルを避け、端点が一致することと撤去後の衝突解除を確認する。実Clientは追加の中間パネルを置き、移動後のRendererキャッシュから経路を取得して迂回と描画範囲を確認する。

`GaugeWireOccupancyTest`は配線の重複・交差・逆向き区間、共通端点、他配線途中の端点化拒否、並走と閉塞時の保留を検証する。全4壁方向のGameTestでも既存配線の迂回を確認する。実ClientはChunkをまたぐCitizen配線同士、およびFactory Gaugeの既存配線との交差を用意し、描画順によらず経路が重ならないことと同一キャッシュ再利用を確認する。画像は`build/gauge-client-wire-crossing.png`と`build/gauge-client-wire-factory.png`。

起動設定は`src/test/citizen-gauge-tests.gradle`、専用Worldは`run/gauge-tests/`へ保存する。

```powershell
.\gradlew.bat -PjeiEnabled=false runGaugeTestServer
.\gradlew.bat -PjeiEnabled=false runGaugeTestClient
.\gradlew.bat runGaugeTestClient
.\gradlew.bat -PjeiEnabled=false -PgaugeResume=true runGaugeTestClient
```

Client検証はFactory Gauge風GUIの＋から壁面ゲージ間を配線し、移動ボタンから1枚を移す。ダイヤモンド縁・Sourceスポーンエッグ・District空表示・記録済みItem光沢を表示し、`build/gauge-client-move-preview.png`と`build/gauge-client-wall.png`も記録する。最後のコマンドは直前のGauge専用Clientが作ったWorldを別プロセスで再起動し、移動後の1枚＋元の3枚・Gauge／District UUID・Link・設定・Citizen Registryと予約解除を確認する。先に通常の`runGaugeTestClient`を成功させる。

`CitizenGaugeAssetTest`はFactory Gauge親モデルの直接継承と、Create本体の16×16テクスチャに対して縁44画素だけが色違いであることを確認する。モデル・テクスチャ変更時は`runData`後に実行する。

ゲージの指定素材は`textures/item/gauge_district_icon.png`（UUID色）と`gauge_influx_icon.png`／`gauge_outflow_icon.png`（無着色）を使う。Client QAは流入→流出保存→再表示→流入復帰を行い、`build/gauge-client-source.png`／`gauge-client-outflow.png`へ記録する。最後に隣接地区を専用Worldへ一時追加して`build/gauge-client-dual-boundaries.png`を撮影し、両色の内側境界を確認して撤去する。`DistrictBoundaryMeshTest`はChunkをまたぐ共有境界・1/16blockオフセット・負座標・同地区内部の線なしを検証する。

District専用画面は統計・名前編集・半径編集・地区IDコピー・保存を検証し、移動と別プロセス再起動で地区名保持を確認する。`build/gauge-client-district-edited.png`、`build/gauge-client-district-hover-{green,red}.png`は新GUI・壁面HUD・実人口変化によるランプ同期の確認用。Source画面は200×160の外枠を維持し、接続・移動・保存だけのレイアウトを確認する。

`build/gauge-client-district-reset.png`は地区名を空欄保存してUUID表示へ戻した画面。UUIDは編集値へ入れず、フォーカス時に空欄から入力できることと、全文の表示幅を検証する。Dedicatedではパネル消失・BEだけの消失の登録削除と、未ロード地区・市民・兄弟パネルの保持を確認する。

JEIありの`runGaugeTestClient`は地区／供給ゲージ・市民・ワードローブ・マネキンでJEIが非表示になり、通常インベントリへ戻ると表示されることを検証する。JEIなしでも同じ実メニューを起動する。`build/gauge-client-copy-tooltip.png`は保存ボタンより前面のコピー説明、地区GUI画像は48pxの帯の上下余白と右下のゲージプレビューを確認する。

Sourceは接続・移動・保存の可視ボタンが3個だけで、旧接続領域がスクロールを受けないことを確認する。地区アイコンのUUID／RGBはGUI・壁面・リネーム・移動で一致する。`CitizenGaugeAssetTest`はDatagen後の白ベース・透明背景・濃い縁・グラデーション、`DistrictGaugeIconTest`はRGBの安定性を検証する。Client QAは実CitizenModelに対しChildの歩行位相1.5倍とAdult／振幅不変も確認する。

Sourceは高さ104pxと2ボタンの縦中央配置、壁面アイコンはFactoryと同じ合成倍率0.2578125を確認する。`DistrictGaugeIconTest`はUUID.hashCodeの下位24bit、色相・彩度を維持した明度90%補正（最大成分230）、黒から無彩色への補正も検証する。

流出の検証は`CitizenPopulationGameTests`で余剰1人の削除・目標割れ防止・Registry除去・cooldown、未ロード地区の不足・強制ロード防止・FORCE保護、既存Citizenの地区間移動優先を確認する。JUnitはネットワークの64ノード段階構築・人口差分更新・設定変更による破棄、流入より既存Donorを優先するBFS、目標人口≦半径と旧値の保存互換性を確認する。Clientは初回init時のOUTFLOW情報とグレー帯の切替を検証し、範囲外入力は`build/gauge-client-{radius,target}-warning.png`へ撮影する。境界メッシュはワールド端でも1/256の精度を保つことを確認する。

中心の重複登録は`CitizenPopulationTest`で座標索引のロード・設定・移動・旧重複データの段階削除、`CitizenPopulationGameTests`で地面記録拒否時のItem不変、範囲内別地点の許可、記録済みItemの重複設置拒否、未ロード地区の判定と削除後の再利用を検証する。

未設置Gaugeの地点予約は、JUnitで予約所有者・永続化・旧形式互換・不正／重複予約除外・地区化・削除・キャッシュversion維持を検証する。GameTestでは、壁設置前に別の空Gaugeで同地点を記録して拒否されること、所有者だけが設置可能なこと、失敗時のItem／予約保持、再記録成功後の旧予約解放を確認する。Client QAでも実際のスニーク入力と2回の使用Packetで再現し、`build/gauge-client-unplaced-center-rejected.png`へ撮影する。消失時の解除は実ItemEntityの寿命切れ・溶岩ダメージ・奈落・killを検証する。分割の一部損失・消滅通知の重複・後からキャンセルされた寿命切れ・拾得・Chunkアンロード・チェスト保管・再読込で誤解除しないことを確認する。Client QAでは実際のCreative画面から記録済みItemをカーソルへ取り、ゴミ箱へ削除して予約解放を確認する。Shift一括削除のネイティブ操作経路も呼び、複数予約を解放する。専用PacketはJUnitで往復と不正個数、GameTestでCreative権限・分割個数・既存地区の非削除を確認する。保管品の期限解除は対象外。

Client QAの重複記録確認は上記の未設置予約を使う。登録済み地区の中心拒否は共通GameTestでも維持する。通常Tooltipは名前・Shift案内・記録済み中心座標を表示し、操作説明はShift詳細内に限る。詳細は現行の地区設定・接続追加・移動・流入／流出を説明し、座標は重複させない。`GaugeTooltipClientChecks`が実TooltipModifierとCreateのShift説明データ、動的モード・中心座標を検証し、`build/gauge-client-tooltip-{normal,shift}.png`へ撮影する。詳細画像はOSキー注入ではなくCreateのShift用Component列を描く。

全タイプのMarker地区保存は`CitizenRouteGraphTest`と`CitizenPopulationGameTests`でタイプ変更・NBT再読込・地区新設／拡縮／分割／削除による自動所属を検証する。Gateway移動先は直接Gauge Link先だけを列挙し、未登録／通常Marker・改名・Link切断・削除のキャッシュ無効化も確認する。`CitizenPopulationTest`は空間索引と線形探索の一致、負座標・範囲境界・接続先キャッシュ・各hop／中止時の予約集計を確認する。`DistrictTerritoryTest`は半径1の3×3区分、中心間境界とUUID同距離判定、Palette＋RLE保存、遅延復元、局所無効化、512枚LRU、密な400マーカーの64件／tick予算と中断再開・適用済みロードの走査省略を検証する。Client QAは範囲変更による自動所属／解除・タイプ変更での維持、読み取り専用所属欄と設定行の順序、8地区の一覧と7行スクロールを検証し、`build/gauge-client-marker-gateway-{destinations,scrolled}.png`へ撮影する。大規模Worldの実TPS/FPS計測とは区別する。

所属地区名表示は`RoutePacketsTest`で未所属・UUID初期名・日本語・64文字名の通信往復を確認する。Gauge Dedicatedでは市民／経路マーカーの地区名、改名・独立・地区削除時の表示解除を検証する。Clientは`build/gauge-client-citizen-district.png`と`build/gauge-client-marker-district.png`を撮影し、翻訳読み込み・画面を開いたままの改名・未所属化も検証する。それぞれの`-district-long.png`／`-district-long-tooltip.png`は64文字名の省略・全文ホバーの確認用。マーカーのJEI表示設定は変更しない。

`DistrictBoundarySurfaceTest`は境界線の足元上限・Slab・頭上の屋根除外・支持面なし・高さキャッシュの20tick更新・64列／tick予算・8192上限・待機FIFOを検証する。Client QAは実ChunkのSlab＋屋根と未ロード列の除外を確認し、境界／プレビューの地面固定をワールド画像へ撮影する。市民の行動タブは目的地y177、所属地区y192、既存2ボタンy207の順を確認し、長い地区名Tooltipはy196で撮影する。マーカーの自動登録説明文は表示しない。

無制限徘徊は`CitizenBehaviorStateTest`で保存値0・既定「-」と既存有限半径の保持、`CitizenWanderAreaTest`で地区所有区分・近隣32blockの接近・独立・範囲変更の無効化を確認する。`CitizenPopulationGameTests`は実際に歩かせて加入・地区内徘徊・範囲変更後の帰還・独立維持を確認する。`CitizenBehaviorGameTests`は無制限のコマンド／GUI Packetと村人との差（火のMalus・FOLLOW_RANGE）を検証する。Client QAは実ホイールで`-→1→-`を確認し、`build/gauge-client-citizen-unlimited.png`と`build/gauge-client-citizen-unlimited-options.png`へ撮影する。既存GUIのホイールは上回しが減少、下回しが増加。

`CitizenWalkHomeGameTests`は隔離batchで帰還Configを200tickへ短縮し、終了時に復元する。実Marker上のWALKによる地区越境・自然帰還と、地区外だけのGraphからRETURN_HOME／到着／WALK継続を確認する。Phoneの両手観測は`CitizenPopulationGameTests`で編集権限と分離して検証し、Client QAで125tick待って古いGauge同期の期限後に撮影する。画像は`build/gauge-client-phone-boundaries.png`／`build/gauge-client-offhand-phone-boundaries.png`。Phoneをしまった後の非表示条件も確認する。

```powershell
.\gradlew.bat test --tests '*ResourceIntegrityTest' --tests '*GarmentResourceValidationTest'
```

Citizen経路だけの実サーバー試験は次で実行する。専用起動ごとに新しい`run/route-tests/<実行ID>/`を使う。
`routeTests`を省略すると既存のCitizen経路試験と専用の移動試験を実行し、指定するとメソッド名をカンマで選択する。
選択名に対応する試験がなければ起動時に失敗する。

```powershell
.\gradlew.bat -PjeiEnabled=false runRouteTestServer
.\gradlew.bat -PjeiEnabled=false '-ProuteTests=diagonalWalkDoesNotFollowAGridZigzag,childWalkSmoothlyAcrossANegativeDiagonal,shortcutsRejectUnsafeGroundAndBodyCollisions,anObstacleOnAShortcutReplansAroundTheWall' runRouteTestServer
```

斜め移動は実際の軌跡長・方向修正回数を検証し、短絡は障害物・頭上・穴・液体・段差・危険Block・未ロードで拒否する。
移動中の壁追加では、経路再計算が共有Schedulerへ入り、迂回して到着することを確認する。

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
Destroyまたは共通銀Tagがなければ亜鉛製法を自動で使用する。Destroy＋銀が揃った環境で亜鉛製法も追加する場合は、Worldの`serverconfig/createapparel-server.toml`で
`forceMirrorCoatingFallback = true`に設定し、Worldを再起動する。

### 自動検証

`RecipeRouteSelectionTest`はDestroy有無×銀Tag有無×2製品の強制設定の全16組合せを確認する。
銀の条件は鏡だけに適用し、強制ONは有効なDestroy製法を保持したままFallbackを追加する。
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
.\gradlew.bat -PjeiEnabled=false -PdestroyEnabled=true -PsilverEnabled=false -PrecipeTestCase=C runRecipeTestServer
.\gradlew.bat -PjeiEnabled=false -PdestroyEnabled=true -PsilverEnabled=false -PrecipeTestCase=D runRecipeTestServer
.\gradlew.bat -PdestroyEnabled=true runJeiTestClient
.\gradlew.bat -PdestroyEnabled=true -PjeiMirrorFallback=true runJeiTestClient
.\gradlew.bat -PjeiEnabled=false -PdestroyEnabled=true runJeiTestClient
.\src\test\validate.ps1 -OptionalRecipes -Destroy
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

ケースA/BはDestroyなし、C〜FはDestroy＋銀あり。C/EはMirrorをDestroy製法のみ、D/FはDestroy＋Fallbackとする。
`-PsilverEnabled=false`は検証runtimeからThe Silver Ageだけを外す。C/Dで鏡の自動FallbackとNylon製法の継続を確認する。
`SilverIntegrationGameTests`は実銀Tag、銀種・反応量論・4.5 molの実消費、錯体化、NBT往復、
最終Mixingの実液体消費と濃度制限、空の銀Tag ContextでのDestroy製法の読み込み拒否とFallbackの有効化を確認する。
銀アクセサリー素体は鉄／実The Silver Ageの銀ナゲットでCompactingし、ネックレス3個・ブレスレット2個の消費、
不足時の拒否、銀Tag未導入時にEmpty Tagのバリアを材料として扱わないことも確認する。
JEIの追加検証は、同期された最終Recipeに応じて化学2反応が表示／非表示になることと、
標準Mixingレイアウト・銀原子の表示モデル・Datapack再読込を確認する。
専用Client LauncherはForgeの実ソート済みタブでCreate本体→Create建築→Apparel→衣類の順序も照合する。
`run/phase25/server-<case>/<実行ID>/world/serverconfig/`に検証専用設定を作成する。
`-PrecipeTestRunId=<ID>`で同じ検証Worldを再使用して再起動を確認できる。
JEIなしの`runJeiTestClient`は同一JVMで3つの新規Worldを順に起動し、SERVER設定の
`false/false → true/true → false/false`と停止時の選択クリアも確認する。

追加方式（2026-10-17）はDatagen／build・JUnit794件、A〜FとC/Dの銀なし各11件・計88 GameTestが成功した。
指定Destroyフォーク0.1.3-i+2でも、両製法共存／銀なしのJEI検索・化学反応表示・Datapack再読込が成功した。
当時の通常Dedicated全体は新規Worldの2回とも224/226成功・Citizen経路試験2件失敗で、失敗対象も変動した。
後続調査で、Forge GameTestの通常地形から地下試験場へ水が流入し、Markerが破壊されていたことを確認した。
上記の固定flat化だけでも元のNavigationで全226件が成功した。安全な斜め移動の改善後もbuild／JUnit799件・全226件と専用移動4件が成功した。
既存試験の期待値・期限は維持し、実プレイのMarker／水流の仕様は変更しない。Client目視はこの追加変更の確認には含めない。

以下は強制Fallbackを排他的切替としていた変更前の検証履歴。現行の前提条件・追加Fallback方式の結果とは区別する。

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
旧設定は自動移行しない。Destroy＋銀ありでも従来の亜鉛製法を追加する場合は新しい
`forceMirrorCoatingFallback = true`を明示設定する。変更後はWorld／Serverを再起動する。
Destroy未導入なら、両設定がfalseでも自動的にFallback製法を使用する。
Destroy導入時も共通銀Tagが空なら鏡は自動Fallbackとなる。以前trueでDestroy製法を隠していたWorldも、
更新後は前提条件が揃えばDestroy＋Fallbackとなる。既存の設定値の変換は不要だが、World／Serverを再起動する。

## Citizen MSPT／FPS原因調査

Client QAは同一GeometryでHeadの髪RGBだけを変える`head-varied`も別caseで測る。各caseを停止して報告した後、Render間で実Native cacheをclearし、解放通知数が所有Entry数と一致し、残数0になることをassertする。`CITIZEN_FPS_PROFILE`のEntry数はclear前、`CITIZEN_CACHE_RELEASE`は検証用clearの結果。定点の保持と、移動によるLRU再生成の頻度は別の試験である。

正式なCPU比較には`.\src\test\profile-citizens.ps1 -Offline -Steady`を使う。実時間で最低10秒＋120tickのwarmup後、2000tick採取する。ForgeのGameTestServerは通常20TPSの待機を行わないため、120tickだけではJITの立ち上がりを除けない。`-CaseId cpu-100-on-1,cpu-100-off-1`等で対象を限定できる。既存run IDがある場合は上書きせず停止する。nanoTimeはCPU側の経過時間で、ThreadMXBeanの実CPU消費時間ではなく、GCやpreemptionも含み得る。LegacyのScheduler.lastNanosは同tick内のGraph処理を含むため、新しいentityTick等のinclusiveへ単純加算しない。

Clientの`/apparelprofile frames`は詳細CPU／cache counterを止め、フレーム間隔だけを採る。自動試験では`-PgaugePerformanceFrameOnly=true`を指定する。詳細OFFのCPU／画面内数は未計測として扱い、NaNや空counterをゼロ負荷の証拠にしない。詳細ON／OFFを同じ描画条件で比較し、計測器の負荷を確認する。

通常時は計測OFF。ServerはOPの`/createapparel gauge profile start`／`stop`、詳細は`/createapparel gauge stats`。DimensionごとのCitizen処理時間を、Server START～ENDの経過時間で割る。`entityTick`はVanillaの親Tickも含む。`mobAiStep`にはVanillaの移動・衝突と独自Goal／Navigationが入るため、AIだけの時間として扱わない。`exclusive`も計測していないVanilla内部処理を含む。親子のinclusiveを合計しない。per-call分位点とper-server-tick分位点を別記し、履歴上限は各4096。Entityの計測数とprofile内の一意UUID数を併記する。Profiler自体のCPU／一時Scope割当も測定値に含まれる。

専用の新規Worldだけで、ロード0／50／100／300／500／1000体、100体の計測ON／OFF各3回、AI／人口停止、JFRを順次実行する。既存の同名Worldがあれば上書きせず拒否する。再実行時はスクリプト内のrun IDを新しいものへ変える。

```powershell
.\src\test\profile-citizens.ps1 -Offline
.\gradlew.bat -PjeiEnabled=false -PgaugePerformance=true -PgaugePerformanceCount=100 -PgaugeTestRunId=cpu-custom runGaugeTestServer
.\gradlew.bat -PjeiEnabled=false -PgaugePerformance=true -PgaugePerformanceCount=100 -PgaugeTestRunId=cpu-ai-custom -PdebugDisableCitizenAutonomy=true -PdebugDisableCitizenBehaviorGoal=true runGaugeTestServer
.\gradlew.bat -PjeiEnabled=false -PgaugeScale=true -PgaugeScaleCase=2 -PgaugeScaleProfile=true -PgaugeTestRunId=mixed-custom runGaugeTestServer
.\gradlew.bat -PjeiEnabled=false -PgaugePerformanceClient=true -PgaugePerformanceCount=100 runGaugeTestClient
.\gradlew.bat -PjeiEnabled=false -PgaugePerformanceClient=true -PgaugePerformanceFrameOnly=true -PgaugePerformanceCount=100 runGaugeTestClient
```

CPU専用試験は固定UUID／乱数seed、平坦床、同じforced chunk範囲、120tick warmup＋400tick、半径8のWANDER。全員が実際にTickすることをassertする。Gauge／Markerは置かず、人口・経路Graphの負荷の証明に代用しない。0体は同じ生成条件の無Citizen対照で、ユーザーWorldのCitizenを削除しない。混合試験は既存のScale Fixtureを使い、実ロード100体の初期ModeをWALK／WANDER半数ずつにする。実Gauge16枚・Marker32個と、残りの未ロードRegistryを区別する。`-PgaugePerformanceJfr=true`は採取区間だけJDK17 Flight Recorderを動かし、隔離World内`citizen-cpu.jfr`へ保存する。JFR追加負荷は通常のON／OFF比較へ混ぜない。

Server診断スイッチはJVMプロパティ`apparel.diagnostics.<name>`。`debugDisableCitizenAutonomy`／`debugDisableCitizenBehaviorGoal`／`debugDisableCitizenPopulationTick`／`debugDisableCitizenRoutePlanning`はいずれも既定false。`-Dapparel.diagnostics.testWorld=true`が無い有効化は拒否する。専用Gradle起動だけこの指定を行う。起動時固定で、通常のConfig／GUIや永続NBTには保存しない。

- Autonomy停止：自主WANDER／WALKと新たな自主Intentを停止する。既存の明示Intent／FOLLOW、Vanilla Mob Tick・Navigation、視線Brainは停止しない。
- BehaviorGoal停止：Goalの本体だけをスキップする。Mob全体のTick・GoalSelector・Navigationや、既にQueueへ入った探索を停止するものではない。
- PopulationTick停止：人口Managerの定期処理・Gauge巡回・Network／Request／Marker地区同期を止める。Entity参加・退出・Intent側の所属更新や予約消費通知は維持する。
- RoutePlanning停止：Graph Plan要求はpendingとし、探索sliceを止める。既存経路／Vanilla Pathfindingは残る。待機・期限超過・STUCKED等への副作用があり、長時間の通常プレイには使わない。

Clientは`/apparelprofile start`／`report`／`stop`で`logs/latest.log`に出力する。`/apparelprofile switch <name> <true|false>`で`debugDisableCitizenGarmentRendering`／`debugDisableCitizenBodyRendering`／`debugDisableCitizenCosmeticRendering`を切り替える。Body停止はCitizen renderer本体・名前・shadowをスキップし、Garment停止は装備衣服だけを止める（Body／標準下着は維持）。Cosmetic停止はhat／追加髪Geometryのみで、Head textureへ合成済みの髪pixelは残る。装備・State・Server Entityは変更しない。

FPSはRender START間隔、平均は全採取区間、p95／p99・1% Lowは直近8192間隔。1% Lowは遅い上位1%の平均フレーム時間の逆数。Menu／pause／非WorldのFPS区間は除外する。Renderer計測はCPUでありGPU実行・driver待ち・buffer flush・shadow／dispatcher全体を網羅しない。主視点のRenderer呼出しに到達した一意ID数と描画回数を分け、鏡passは別counter。Texture切替はbuffer要求の変化であり、実GPU bind回数ではない。RenderType数はtexture別instance数で、shader種類数ではない。

Client自動試験は別の新規World・1280×720・描画／simulation距離6・VSync OFF・FPS上限なし（設定260）・FOV70・Entity距離倍率2・view bobbing OFF・固定飛行視点、初期POSEのCitizenを使用する。A／B／Cと種類循環・衣服なし・複数PartのCoat・色柄別を順に測る。各caseのcacheを採取直前に明示clearし、cold初回生成も含む。B／Cは同じServer Entityと装備を保持する。Gの装備変更はQA生成Entityのみ。種類数を超える人数では同じGarment定義を循環するため「全員別定義」と扱わない。Integrated ServerのCPU競合を含み、Dedicated ServerのMSPTや独立Clientの描画性能と混同しない。各ロード数を変えて別JVMで実行する。

Garment Cacheは1024、Citizen head Cacheは256の既存保持目標とframe使用中保護を変更しない。HITは直接Reference再利用も含み、MISSはfactory呼出し、EVICTIONはframe-end LRU、RELEASEはclear（それぞれDynamicTexture解放）。失敗時の生成済みTexture破棄は別counter。Compose inclusiveからUpload子Scopeを差し引くexclusiveは画像読込・合成等のCPUで、GPU upload所要時間そのものではない。生のGPUメモリ・driver内部保持量は未計測。上限引上げの判断にはMISS／再生成／解放頻度と実GPU計測の両方が必要。

頂点counterはPart／face集合単位でまとめて更新する。QuadごとのMap更新による計測負荷を避けるためであり、Geometryや頂点送信順を変えない。FPS比較でも計測ON／OFFの別を記録する。`CITIZEN_LOADOUT_CASE`に使用した衣類IDと定義数、`CITIZEN_CLIENT_DEVICE`にGPU／OpenGL／Javaを出力する。生成したQA Worldと生ログは`run/gauge-tests/`および`build/citizen-performance-*.log`に残す。

QAはwarmup中の40～80tickだけ別の詳細collectorを動かし、`CITIZEN_VISIBILITY_CENSUS`へServerロード数／Clientロード数／主視点描画対象の一意ID最大数を記録する。FPS採取は120tick時点でcollectorをリセットして開始し、census時間を混ぜない。初期POSE・固定位置の試験に限った別窓のcountであり、動く実Worldの同時可視数の代用にはしない。`-PgaugePerformanceCaseCount=1`はAだけの短い再確認（既定8は全case）。

Geometryの頂点数・RenderType・Texture要求切替は`CommonGarmentRenderer`の衣服／support範囲で、VanillaのHead ModelPartやWorld全体を網羅しない。詳細CPUは完了したrender frame、FPSはSTART間隔を数えるため境界に最大1frameの差がある。CPU/frameは詳細collectorの完了frame数で割り、CPU割合は近似として解釈する。ShaderのGPU実行時間や実bind数の代用にはしない。

### Envelope共有頂点描画の比較

```powershell
.\gradlew.bat test --tests '*EnvelopeMeshTest' --tests '*DualEnvelopeDeformerTest'
.\src\test\profile-envelope.ps1 -Offline -Images
.\src\test\profile-envelope.ps1 -Offline -Counts 100 -Reverse
.\src\test\report-envelope.ps1 | Format-Table Run,Count,Backend,Mode,Case,FPS,EnvelopeMs,AllocBytes
```

`profile-envelope.ps1`は50／100／300／500／1000体を別JVMで順番に測定する。既存の`build/envelope-<count>[-reverse].log`または`-client.log`があれば上書きを拒否する。`JAVA_HOME`にはJava 17を指定する。他の高負荷試験は並行実行しない。

1 World内の4段階は旧frame-only→新frame-only→旧detail→新detail、各段階にA／B／Cを含む。`-Reverse`で旧／新の順序を逆転する。視点、装備、個体、距離は同じで、各case120tick warmup・200tick採取、cache clear直後のcold生成も含む。旧経路は診断専用`GarmentDiagnostics.debugUseLegacyEnvelopeRendering`（既定false）。通常のConfigや保存データに含めない。Client commandの同名switchでも比較できる。

`-Images`は通常／slim × flat／bust × 6姿勢 × 7装備を同じoffscreen条件で旧／新描画し、空画像を拒否したうえで全pixelを比較する。画像は`build/envelope-images/`、成功markerは`ENVELOPE_IMAGE_COMPARISON_PASSED`。静止・歩行／走行相当の脚角度、非対称腕、しゃがみ、座り、スカート、コート、帽子、バッグ、重ね着、染色・柄を含む。動画全体の時間変化の画像比較ではない。

CPU内訳は`envelopePoseBuild`、`envelopeVertexTransform`、`envelopeFaceNormals`、`envelopeVertexWrite`、親`envelopeGeometrySubmission`。旧経路は各Quadで処理が交互になるため親合計だけを測る。親子inclusiveを合算しない。`envelopeRenderTypeGets`はCPU側の取得呼出し数で実GPU bind数ではない。共有位置数とcorner参照数、送信頂点数を分ける。Citizen本体はpose／body support／underwear／head／cosmeticのCPUを別記する。

`envelopeRenderTransform`は変形後位置と面法線への描画行列適用。位置は共有位置ごと、法線は面ごとに計算する。最終属性はVertexConsumerの全属性一括APIへ渡す。画像比較は実BufferBuilder経路で行い、別passの記録用Consumerで送信属性列もbit単位比較する。記録wrapperを通してfastFormat経路の画像比較を代用しない。

detailだけで`ThreadMXBean`のRender Thread frame当たりJava heap allocationを採る（JVMが対応し既に有効な場合）。計測器自身やWorld描画も含み、native/GPU allocationではない。未対応／frame-onlyはNaN。細分計測が増やすallocationも含むため、衣服変形だけのallocation削減と断定しない。GPU時間と実VRAMは別途未検証とする。

### Citizen LOD・遮蔽・Rigid頂点送信の比較

```powershell
.\gradlew.bat test --tests '*CitizenLodTest' --tests '*CitizenSupportOcclusionTest' --tests '*GarmentTexturePixelOpsTest'
.\src\test\profile-citizen-lod.ps1 -Offline -Images -Run main
.\src\test\profile-citizen-lod.ps1 -Offline -Counts 100 -Images -Reverse -Reload -Run reverse
.\src\test\report-envelope.ps1 -Logs (Get-ChildItem build/citizen-lod-*-main-client.log).FullName |
    Format-Table Count,Case,Mode,FPS,GarmentMs,BodyMs,Vertices,AllocBytes
```

規模は50／100／300／500／1000体。各JVMで9 caseをframe-only→detailの2段階で実行する。

| case | 条件 |
|---|---|
| lod0-legacy | 全員LOD0、従来のRigid頂点送信、遮蔽OFF |
| lod0 | 全員LOD0、一括Rigid頂点送信、遮蔽OFF |
| lod1 / lod2 | 同じ配置・視点で全員のMesh LODだけ固定、遮蔽OFF |
| lod-auto | 同じ配置でCamera距離から自動LOD、遮蔽ON |
| occlusion | LOD0固定、遮蔽ON |
| distance-mix | 近中遠に分散、半数がWANDER、自動LOD・遮蔽ON |
| lod-varied-legacy / lod-varied | 多種類・染色の同一配置、旧／新Texture合成、他の条件は同一 |

Texture coldは採取開始時のCache clearを含む。旧Texture合成は従来のmask演算・Optional overlay、旧Rigidは従来のMatrix overload／属性別送信に切り替える診断用経路で、古いjarとの比較ではない。既存のEnvelope高速化は両方で維持する。`-Run`で結果名を変え、既存ログは上書きしない。`-Images`は各JVMで実行し、PNGとCSVは直近runの`build/citizen-lod-images/`、成功markerは各runの生ログへ残す。

画像比較は4体型×6姿勢×7装備×6方向×3LODの3024シーン。LOD0は旧Rigid＋遮蔽OFFと新Rigid＋遮蔽ONの全pixel一致を要求する。LOD1／2はそれぞれ20／48ブロック相当の投影サイズで完全Meshとの輪郭差を測る。近景画像の単純縮小ではなく同じRaster条件で再描画する。自動の輪郭差上限8%は退行検出のguardであり、許容品質が人間によって承認されたという意味ではない。連続動画・実際の街でのPop-in・すべてのResource Packは別途確認する。既存Envelope画像比較は送信属性列もbit単位比較する。

Vanilla Culling QAは、Frustum呼出しが1回だけであること、拒否が尊重されること、十分遠いEntityはFrustum前に拒否されることをassertする。LOD3はLOD2 MeshのままVanillaの距離判定に従い、独自カリングを追加しない。Boundsは既存Vanillaの`.inflate(.5)`を維持する。このテストはすべての衣服・姿勢が常にBounds内という証明ではない。

通常の距離／hysteresisはClient Configの`citizenRendering`。`/apparelprofile lod -1`は自動、0～3は診断用固定。`switch`に`debugDisableCitizenLod`、`debugEnableCitizenOcclusion`、`debugDisableCitizenOcclusion`、`debugUseLegacyRigidRendering`、`debugUseLegacyTextureComposition`を指定して比較できる。遮蔽は省略効果0という測定結果により通常OFFとし、比較時だけ`debugEnableCitizenOcclusion=true`かつ`debugDisableCitizenOcclusion=false`にする。装備・永続Stateは変更しない。GUIはLOD0、Mirrorは反射Cameraからの独立LODを用いる。TOP装備時の標準上半身下着非描画は従来ルールを維持する。

`-Reverse`はLOD caseの順序を反転する（同じWorld内のframe→detailは維持）。`-Reload`は全採取後に実Resource Reloadを行い、旧snapshotのGeometryが交換され、Textureを再取得でき、装備NBTが保持されることをassertする。Reloadの時間はFPSの採取窓に含まない。

Texture演算短絡の効果が安定しなかったため、通常の`debugUseLegacyTextureComposition`は既定true（従来演算）。falseで選ぶ新演算は診断専用とする。LOD比較のFixtureはcold演算を全caseで新演算へ統一し、`lod-varied-legacy`のみ従来演算へ切り替える。標準のcold演算復帰後に全規模の絶対FPSを再採取した結果ではないことに注意する。

`lodNCitizens`は描画呼出し数、各frameの一意数や保存Citizen総数とは別。`lodNVertices`はCommonGarmentRendererのGarment／Support範囲で、Native Head等を含まない。`observedGeometryMeshes`／`observedTopologyBytes`は採取中に使ったMeshのprimitive索引・UV配列だけで、全Geometry Cache Heapではない。`supportOcclusion`と省略数を別記し、省略0なら高速化として扱わない。不透明性は幾何候補が実際に必要な場合だけsource画像から最終alphaを再現し、Texture Cacheと同時に解放する。Texture画像全体の追加走査を全Citizenへ一律に行わない。

`citizenGarment`は今回CitizenでFrameを一度だけ構築・共有した後の衣服描画時間。`apparelRenderFrame`／`envelopePoseBuild`も別記し、以前Frame構築を内包していたGarment時間と単純比較しない。親子inclusiveの加算は禁止。LODごとの時間もGarment時間の子である。Render Thread allocationはProfiler込みのframe全体で、LOD Geometryの静的保持量やGPU使用量ではない。

### Citizen追加描画比較

OSのクリップボードがアクセス拒否となる環境では、通常GUI QAの`-PgaugeSkipClipboard=true`でコピー1項目だけを明示的に未検証として分離できる。既定falseで通常のassertは維持する。この場合の完走マーカーは`CITIZEN_GAUGE_CLIENT_PARTIAL_PASSED`であり、通常の`CITIZEN_GAUGE_CLIENT_PASSED`ではない。ログの`CITIZEN_GAUGE_CLIENT_CLIPBOARD_SKIPPED`を確認し、全GUI検証済みとは報告しない。クリップボードを利用できる対話環境で、別途通常QAを行う。

実ワールドでは視点・解像度・描画距離・Entity倍率・VSync・上限FPSを固定し、`/apparelprofile compare 10`で6条件を順に採取する。各条件は40 Client tickの待機後、指定秒数×20 Client tickを記録する。通常→Garment OFF→Citizen描画OFF→鏡OFF→LOD3固定→Citizen Shadow OFF。CitizenはServerに残り、装備・NBT・ワールドの永続データは変更しない。Client tickが追い付かない場合は指定秒数より実時間が長くなり、FPSは実測フレーム間隔で算出する。

細分CPU計測自体の影響を分離するため、同じ条件で`/apparelprofile compareframes 10`も実行する。これは同じ6条件をフレーム時間だけで比較し、各LOD・Citizenの詳細counter／CPU時間は採取しない。NaNやcounter未出力は未計測であり0負荷ではない。CPU時間の表とFPSだけの別窓を混ぜて負荷割合を算出しない。

比較前のスイッチを終了・`/apparelprofile cancel`・ログアウト／Dimension変更時に復元する。`/apparelprofile reset`は診断スイッチを通常既定へ戻し計測を終了する。比較中はカメラや設定を変えず、メニューを開かないこと。メニュー中は採取進行を止める。ログ`CITIZEN_LIVE_COMPARISON`へ各条件終了時のカメラ・設定とCPU/FPSを記録する。同じ視点を自動固定する機能ではなく、移動した結果は比較から除外する。事前にOFFだった機能は通常caseでもOFFのため、標準比較はまずresetする。

個別の比較は`/apparelprofile switch <name> true/false`。追加名は`debugDisableMirrorRendering`、`debugDisableCitizenShadows`、`debugEnableReflectionCitizenLod`、`debugUseIndexedRigidRendering`。鏡OFFでは既存反射Cacheを解放し、復帰時に再取得するためwarmupを確保する。Shadow OFFはCitizenだけの半径を0とし、他Entityや地形の影設定を変えない。反射LODは独立した鏡viewごとのhysteresisを使い、主視点の状態やGUIプレビューLOD0へ書き戻さない。共有Rigid変換と反射LODは検証後の標準ON（各enable/useスイッチ既定true）、Shadow非描画・鏡非描画は診断時だけON（disableスイッチ既定false）。

`lodNCitizen`はsnapshot／LOD選択後からBody・Head・GarmentまでのCPU経過時間で、`citizenRenderer`の子。`primaryCitizenRenderer`と`reflectionCitizenRenderer`も同じ親の内訳で合算に注意する。`reflectionLodNCitizens`は反射描画呼出し回数。主視点の一意Citizen数とは区別する。`renderTypeRequestsChanged`と`textureBindingRequestsChanged`はHead＋共通Garment／Support範囲の要求変化で、実GPU bindやdraw回数ではない。送信頂点counterは従来どおりGarment／Supportに限り、Native ModelPartのHead／HairやShadowは含まない。

```powershell
.\src\test\profile-citizen-submission.ps1 -Offline -Images -Run first
.\src\test\profile-citizen-submission.ps1 -Offline -Reverse -Counts 100 -Run reverse
.\src\test\profile-citizen-submission.ps1 -Offline -Counts 100 -Cases mix-before,mix-after,varied-before,varied-after -Run mixed
.\src\test\profile-citizen-submission.ps1 -Offline -Counts 100 -Cases mirror-before,mirror-lod,mirror-off -Run mirrors
```

同じJVM内で従来の全属性一括送信（before）と共有Rigid位置変換（after）を切り替える。旧Texture演算・遮蔽OFF・既存の自動LODを両方で維持する。静的Meshは両方でロード済みで、過去jarとの常駐Heap比較ではない。caseごと120tick warmup＋200tick採取、frame-only→detailを独立に採取する。従来のA/B/C Fixtureも維持する。

Rigid MeshはElement単位で厳密な座標共有とcorner索引を事前計算し、Poseごとの行列変換だけ共有する。UV・Face法線・送信順・送信数・RenderType・透過性を変えない。`rigidPrimitiveBytes`はその保持配列の合計で、既存BakedFaceやJava objectを含む総Heapではない。`heapUsedBytes`／`heapCommittedBytes`／`jvmDirectBufferBytes`はプロセス全体のreport時点値（peakではない）。Direct Buffer統計はNativeImage・driver等の全native割当や実VRAMを包含しない。GPU時間は別途ツールが必要で、CPU送信時間から推計しない。

### Citizen移動LOD・描画方式の試作比較

試作はgaugeTest source setだけに置く。通常MODへ一体型Mesh・ImpostorのスイッチやRendererを登録しない。既存CitizenRendererの頂点列を共有のpose-baked Meshにまとめる方式と、128×128 RGBAのImpostorをLOD3で比較する。いずれも近距離・鏡・準備待ちでは従来3Dへ戻す。Atlasによる単一drawや完全な動的rigged meshではない。Static MeshのEntity別複製は行わず、外見・装備・Pose・位相・Resource世代をキーに64件まで保持する。

```powershell
.\src\test\profile-citizen-movement.ps1 -Offline -Run
.\src\test\profile-citizen-movement.ps1 -Offline -Counts 100 -Images -Reload -Run
.\src\test\profile-citizen-movement.ps1 -Offline -Counts 100 -Cases 'depart-3d,boundary-3d,mixed-3d,camera-3d,both-3d,teleport-3d,chunks-3d,mirror-3d,mirrorcamera-3d,varied-3d,varied-mesh,varied-impostor' -Run
.\src\test\report-citizen-movement.ps1 -Logs build/<client-log>.log
```

`-Run`なしはコマンド表示のみ。5規模を直列で実行し、新規専用平坦Worldだけを操作する。40 Client tick待機＋200 Client tick採取。NoAI・位置更新はこのFixtureの再現性確保に限定し、通常CitizenのAIやGateway移動の代用テストとはしない。通常は詳細計測込みのFPS、`-FramesOnly`で別窓の軽量FPS比較。`-Reload`は採取窓外で実Reload後に全caseを再試験する（phase 1は詳細計測）。

`CITIZEN_MOVEMENT_WINDOW`はwarmupを除く試作Mesh／Sprite生成。`CITIZEN_MOVEMENT_PROFILE`内の試作counterはwarmup込みのcase全体。元の3Dへフォールバックする回数、64件上限のeviction、資源解放を別記する。Impostor生成は1 game tickあたり1件、未生成でCitizenを隠さない。元のTexture合成Cacheの上限は変更しない。

通常Profilerにも`citizenLodSelection`、`frameMaxMs`、`gcCollections`／`gcTimeMs`を追加。詳細計測で変更の前後5 frame intervalの和集合を`lodTransitionIntervals`／`transitionP95Ms`／`transitionP99Ms`等へ記録する。Frame-only窓では変更counter・詳細CPU・allocationは未計測（NaN等）でありゼロではない。GCは同一JVMのServer等を含むprocess全体の差分。親子CPUを合算しない。試作の再送信にはBody・Head・Garmentが混在するので、通常のGarment子scopeだけで試作全体の負荷を評価しない。

画像QAは4 Profile×6 Pose×7衣服構成×4方向×2方式×遠近＝2688シーン。近距離復帰は全pixel一致をassertし、遠距離の差はCSV／PNGへ保存する。`CITIZEN_REPRESENTATION_IMAGE_MEASUREMENT_COMPLETE`は画質の採用合格マーカーではない。保持した位相Meshと次の動的Poseの差も別画像で確認する。元Texture Cacheと最終姿勢Meshの寿命統合、鏡での試作自身のLOD、実街での連続目視QAが未検証のまま通常へ採用しない。

2026-10-21の採否：両試作とも通常不採用。Meshは同一Poseで一致しても位相内の歩行変形が止まり、衣服／外見混在では64Entryを超えて再生成・割当・GCが増加した。Impostorは輪郭差が大きく、混在時の性能も安定しない。通常は既存3D LODを維持する。全5規模の固定／往復／混在FPS・1% Low・p95/p99と採否は`docs-private/ai-context/2026-10-21-citizen-moving-lod-prototypes.md`を参照する。

### Citizen描画の完成状態と配布監査

通常描画は既存LOD・Envelope高速化・共有Rigid変換・鏡Cameraの独立LODを使用し、全LODで毎フレームのアニメーションを維持する。基本Profilerとライブ比較コマンドは維持するが、計測は既定OFF。不採用のPose低Hz化・変形キャッシュとその専用画像QA／JUnit4件は撤去した。`pose`／`deform`ケースと`-AnimationImages`は使用しない。過去の測定結果は`docs-private/ai-context/2026-10-21-citizen-animation-lod.md`に残し、集計Scriptは過去ログも読み取れる。

`build`に含まれる`verifyProductionRendering`は再難読化後の配布jarを検査し、gaugeTestのクラス・pipeline用Mixin設定・撤去済みPose試作のクラス／APIが混入すると失敗する。Atlas・一体型Mesh・Impostor・固定Buffer・Bulk送信の試作は`src/test/gauge/`に限定され、通常版では利用できない。既存の診断専用遮蔽比較／Texture演算比較は既定OFF／従来演算を維持し、通常経路へ採用しない。

## Citizen描画パイプラインの検証専用試作

通常配布版には含めない。`-Pipeline`（Gradleでは`-PgaugePipeline=true`）でのみoptional Mixinを有効にする。`3d`が現行、`request`が連続Consumer要求の省略、`fixedbuf`が同一Citizen内の固定buffer、`bulk`がForge bulk送信、`atlas`がBody／標準下着4枚だけの有限Atlas。既存のPose低Hz・Mesh・Impostorは使用しない。

```powershell
.src\test\profile-citizen-movement.ps1 -Counts 50,100,300,500,1000 -Pipeline -FramesOnly -Reload -Offline -Run -Cases 'fixed-3d,fixed-request,fixed-fixedbuf,fixed-bulk,fixed-atlas,palette-3d,palette-atlas,approach-3d,approach-atlas,street-3d,street-atlas,spread-3d,spread-atlas'
.src\test\report-citizen-pipeline.ps1 -Logs build/<fresh-client-log>.log -Csv
```

各case120 Client tick warmup＋200 tick採取。`FramesOnly`はphase0の詳細を停止、`Reload`は実Resource Reload後のphase1で詳細を採取する。GPU時間ではない。`palette`は静止した衣服・Profile・肌色・髪色混在、`street`は同構成の接近往復、`spread`は6/20/45/80ブロックへ分散。`occluded`は壁の裏側（独自Entity Culling MODなし）、`mirror`／`mirrorcamera`は既存鏡Fixture。外部Entity Culling・Shader MODを導入済みと仮定しない。

`-PipelineImages`は4試作×4032シーン（上下視点・子どもを含む）、1680連続歩行比較、8透過／不透明順序比較を行う。`CITIZEN_PIPELINE_IMAGES`／`CITIZEN_PIPELINE_TEMPORAL_PASSED`／`CITIZEN_PIPELINE_TRANSLUCENCY_PASSED`を確認する。Native QAは1028頂点の全属性byte一致、詳細OFFでcounter非記録、手動Flush後の再取得、実format guardを`CITIZEN_PIPELINE_NATIVE_PASSED`で検証する。optional hookや試作が実際に動かなければ試験を失敗させ、未計測を0と報告しない。

要求数・変化数と、非空RenderType終了・BufferBuilder終了・実glDrawElements／glBindTextureは別counter。WorldのVanilla経由だけを対象に主視点／反射を分離し、HUD・他MOD直接GL・multi/indirect draw・GPU実行時間は含めない。RenderType別は最大64種類、超過分はtype64。固定bufferのFlushはCitizen既存CPU scopeの外へ移るため、本体CPUの見かけの減少を高速化と呼ばない。

検証Clientだけの`/apparelpipeline 0|1|2|3|4`と既存`/apparelprofile frames|start|stop|report`でも比較できる。`fixed-quiet`では追加probeのみOFFとし、既存Profilerとの差を測る。テストのセーブデータ・装備は変更せず、各case終了・Reload・終了時に所有Native／Textureの解放を確認する。測定中は他build・graph更新を並行実行しない。

採否・全数値・制約は`docs-private/ai-context/2026-10-21-citizen-render-pipeline.md`を参照する。実ワールド・GPU／VRAM・外部MOD互換が未検証なら明記し、単回FPSや要求減少だけで本番採用しない。

自動Fixtureを作らずに対話診断する場合：

```powershell
.\gradlew.bat runGaugeTestClient -PjeiEnabled=false -PgaugePipeline=true -PgaugePipelineInteractive=true --offline
```

初回アクセシビリティ画面またはタイトルへ入り、自動ワールド作成・自動終了はしない。必ずワールドコピーと同じMOD構成／描画設定を用意する。`/apparelpipeline 0`で現行、`4`でAtlas、`/apparelprofile frames`→同じ視点を維持→`/apparelprofile stop`で詳細OFFのFPSを比較し、`start`では詳細counterを採取する。試作は通常配布jarにはない。終了前は`/apparelpipeline 0`、ログアウトでも所有資源を解放する。切替自体はCitizen装備・保存データ・Server AIを変更しない。

### Support Atlas交互順の反復検証

```powershell
.\src\test\profile-citizen-movement.ps1 -Counts 100,1000 -Pipeline -PipelineResourcePack -FramesOnly -Offline -Run -WarmupTicks 120 -SampleTicks 400 -Cases 'fixed-3d,fixed-atlas,fixed-atlas,fixed-3d,fixed-3d,fixed-atlas,fixed-atlas,fixed-3d,street-3d,street-atlas,street-atlas,street-3d'
.\src\test\report-support-atlas.ps1 -Logs build/<100-client-log>.log,build/<1000-client-log>.log -Csv
```

重複caseにtrial番号を付け、画像もtrial別に保存する。隣接するOFF／ONペアのFPS・1% Low・p95/p99・CPU・実Draw／Bindを比較し、順序を無視した単純な全窓平均だけで採用しない。詳細CPU／Drawは`FramesOnly`を外した別実行で採取するか、従来どおり`Reload`後の詳細窓で採取する。frames-onlyの未計測CPU／GL counterはNaNのまま扱う。

WarmupTicks／SampleTicksはServer更新待ちを除いたFixture進行回数で、固定秒数ではない。Profilerは待機中のrender frameも採取する。特に1000体では400進行tickが20秒を超えるため、sampledIntervalsと実フレーム統計を正本にする。

`PipelineResourcePack`はbuild配下へ元画像と同寸法・grayscaleの差し替えzipを生成し、実Reloadで画素・blur／clamp metadataを読み込む。旧Atlas解放→元Consumerへのfallback→pack順序復元→再Reload／Atlas再構築をassertする。`CITIZEN_ATLAS_RESOURCE_PACK_FALLBACK_PASSED`／`CITIZEN_ATLAS_RESOURCE_PACK_RESTORE_PASSED`を確認する。無効なサイズや色のBody PNGは既存loader自体が拒否するため、この有効pack試験と混同しない。

現段階ではvanilla／mod_resources以外のpack、既知のOculus／Iris／OptiFine、非標準Source／実頂点formatを保守的にfallbackする。`supportAtlasFallback`／`supportAtlasFallbackRequests`を確認し、fallback中の値をAtlas有効の測定として扱わない。Shader policy単体テストは実Shader MODの起動試験ではない。Resource世代が変わるまで未対応環境の再構築を毎frame試みず、Reload listener／ログアウト／mode0で所有資源を解放する。

Fixtureの反復改善だけでは採用せず、実ワールドで同一条件の反復改善・tail安定・互換性が確認できるまで診断専用を維持する。結果は`docs-private/ai-context/2026-10-21-support-atlas-validation.md`を参照する。
