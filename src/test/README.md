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

## Citizen描画の品質回帰

通常の描画はEnvelope共有頂点・Indexed Rigid・LOD0～3・鏡Camera別hysteresis・毎フレームアニメーションを使用する。性能調査用のGarmentDiagnostics、/apparelprofile、描画停止／旧経路比較スイッチ、不採用の遮蔽・Texture短絡・Atlas・Mesh／Impostor・固定Buffer・Bulk試作と専用Mixinは撤去した。通常描画へ診断処理を残さない。

品質QAは診断APIや比較用Rendererに依存しない。

```powershell
.\gradlew.bat build -PjeiEnabled=false --offline
.\gradlew.bat runGameTestServer runGaugeTestClient -PjeiEnabled=false --offline
.\gradlew.bat runGaugeTestClient -PjeiEnabled=false -PgaugeRenderClient=true --offline
```

最後のClient QAは新規の専用平坦Worldで実行する。Player用Humanoidの衣服・Body／下着、Citizenの成人／子ども・4 Profile・全身Pose・7衣服、全Mannequin型、LOD0～3、Vanilla culling、接近／離脱、複数鏡の独立LOD、Resource ReloadのGeometry更新・旧NativeImage解放、Chunk離脱時のRenderer cache除去と再ロードを検証する。通常GUI QAではWardrobe／Mannequin／Citizenのプレビューとコピーも検証する。成功markerはCITIZEN_RENDER_CLIENT_PASSEDとCITIZEN_GAUGE_CLIENT_PASSEDで区別する。FPS採取は行わない。

変更前後を直接比較する場合は同じマシン・Resource・テストコードで、変更前に `-PgaugeRenderReference=record`、変更後に `-PgaugeRenderReference=compare` を指定する。build/citizen-render-reference.propertiesにシーンごとのRGBAと頂点属性・要求RenderTypeのSHA-256を保存する。生成Textureの数値IDとJVM固有のShader supplier Lambda識別子だけは正規化し、色・UV・法線・光源・overlay・頂点順は正規化しない。基準が無ければcompareは失敗し、比較漏れも失敗する。off（既定）でも描画の空出力・LOD輪郭・Texture再生成・寿命検証は行うが、変更前との一致とは報告しない。画像QAの全シーンが完了する前の失敗では基準を保存しない。

build内のverifyProductionRenderingは再難読化後のjarを検査し、品質QAクラス、廃止した診断API、試作Mixin設定の混入を拒否する。基準画像・過去ログはbuild配下だけで、配布jarやソース管理へ含めない。

## Server側の性能検証（今回の整理対象外）

CitizenSystemMetrics、Serverの診断コマンド／停止スイッチ、既存の人口・経路・Entity Tick処理は変更しない。専用Worldだけで使用する。

```powershell
.\src\test\profile-citizens.ps1 -Offline -Steady
.\gradlew.bat runGaugeTestServer -PjeiEnabled=false -PgaugePerformance=true -PgaugePerformanceCount=100 -PgaugeTestRunId=cpu-custom --offline
```

ServerはOPの `/createapparel gauge profile start`／`stop`、詳細は `/createapparel gauge stats`。実時間10秒＋120tickのwarmup後に2000tickを採取する。nanoTimeはCPU側の経過時間で、GPU時間ではない。親子のinclusiveを合算しない。既存run IDのWorldは上書きせず停止する。
