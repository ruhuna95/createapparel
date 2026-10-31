# 自動検証

## 実2クライアントのMultiplayer検証

```powershell
.\src\test\run-multiplayer.ps1 -Offline
```

Java 21を使用し、`JAVA_HOME`もJDK 21へ設定する。専用source setの`src/test/multiplayer/`と`multiplayer-tests.gradle`で、127.0.0.1だけに待ち受ける通常Dedicated Serverと、異なる名前のClient A/Bを別JVMで起動する。新規の`run/multiplayer-tests/<RunId>/`だけを使用し、既存RunIdの上書きは拒否する。ポートの既定値は25585で、必要なら`-Port`を指定する。開発環境ではNeoForgeのIDE実行判定を使用し、EULA承諾ファイルは自動作成しない。

Apparelの自己／他Player同期、同じWardrobeからの実C2Sスタイル変更、Mannequin／Wall Hanger／Rack／Citizenの変更同期、Loomの糸表示、通常／Gateway／CrossingのRoute View、死亡と実Respawn、次元移動と再Tracking、両ClientとServerの実Chunk unload／reloadを検査する。続いて同じWorldを別JVMで再起動し、保存されたEntity UUID・装備・表示・Marker IDと再Login同期を照合する。操作指示・期待値・完了通知だけを共有ファイルへ出し、Clientの同期状態は製品の通信経路から受け取った状態で検査する。

全プロセスの起動前にコンパイル・リソース準備を完了し、ModDevGradleが準備した引数でJavaを起動する。試験中に別のGradleから共有`build/resources`を更新しない。ログは`build/multiplayer-<RunId>-<initial|resume>-<Server|ClientA|ClientB>.log`。成功結果は同RunId内の`<initial|resume>-<server|A|B>-passed.txt`全6件で確認する。Launcherの終了コードだけを成功判定にしない。

初回の3成功結果がそろった後、再起動試験だけを続行する場合は`-RunId <既存ID> -ResumeOnly`を指定できる。既存の再起動結果があるRunIdは上書きしない。cold LoginがTimeoutした場合は開始時だけ最大3回の再接続をログに記録し、接続後の切断は再接続で隠さない。

Citizen WALK／Gateway移動／Crossing待機／迂回と予算の詳細は既存Dedicated GameTestも併用する。独立アカウントの認証・外部ネットワーク遅延・別PCのGPU性能・他MODとの互換試験を、このlocalhost検証の結果に含めない。

死亡Fixtureは`keepInventory=true`で、Clientから通常のRespawn要求を送る。Citizenの変更同期・保存FixtureはNoAIの静止個体を使い、実WALK・Navigationと待機条件は共通Dedicated GameTestで別に確認する。

## Escalatedと市民の地形移動

`CitizenEscalatedGameTests`は実Escalated 1.3.2（Minecraft 1.21.1／NeoForge／Create 6.0.10）を使い、通常床より少し迂回する自動歩道の優先選択、成人WALK／子供WANDERでの立ち乗りと降車後の歩行、エスカレーター上下搬送、実モーターの停止・逆転、乗車中の動力停止を検査する。自由歩行での逆進入回避と実迂回、上下エスカレーターの逆走経路除外、動的反転による古い経路の無効化、256rpmで中間目標を越えて降車しても引き返さないことも含む。Escalatedは専用runtimeだけに追加し、通常起動と配布JARには含めない。

64rpmで目的地が自動歩道の横へ2ブロックずれたWALK／WANDERと子供の木製自動歩道について、到着前の逆戻りを0.03ブロックの許容差で検出する。上下それぞれのエスカレーターと横の階段を使い、下段→上段→下段→上段の3区間を実移動し、目的地の更新が継続することも確認する。片道到着だけで往復成功とは扱わない。

側面進入の5件では、X軸の金属自動歩道・Z軸の子供WANDERと木製自動歩道・上下エスカレーター・停止設備と端部について、両側面からの直角／斜め進入を通常費用のfallbackでも除外する。動力付きの平坦2件では、横から出発して入口まで回り、実際に乗車して目的地へ着くことも確認する。

入口までの迂回を含む実経路長にも既定の16block制限を維持する。横2block・前方14blockの自由歩行では、入口経由が制限を超えるため通常床を選べることを専用試験で確認する。優先利用を要求する自由歩行試験は前方13blockとし、範囲内で実乗車・到着・降車後の逆戻り禁止を検査する。診断用に32blockを渡す照会は試験内だけで、製品の探索距離は変更しない。

`-PescalatedTests=true`により公式Modrinth版`maven.modrinth:escalated:vJ49zRJl`をテストruntimeへ取得する。旧Forge版JARの手動配置は不要。

```powershell
.\gradlew.bat runRouteTestServer -PjeiEnabled=false -PescalatedTests=true --offline
.\src\test\validate.ps1 -Offline -Escalated
```

`CitizenNavigationGameTests`には成人／子供の1ブロック段差・バニラ階段の3ブロック上り下り、長い階段の中間目標が床へ埋まらないことを確認する5件も含む。Escalatedなしでも同じ往復試験を行える。経路試験は高い床へ分離し、周辺のFixtureと干渉させない。

### 手動確認用ワールド

```powershell
.\gradlew.bat runEscalatedClient -PescalatedTests=true -PjeiEnabled=false --offline
```

専用環境`run/escalated-client/`で、新しい`escalated-manual-<時刻>`ワールドを作成し、起動したままにする。既存ワールドは上書きしない。Y=80の床上に、Z=0の金属自動歩道、10の木製自動歩道、22の上りエスカレーター、34の下りエスカレーター、46の通常段差、56の階段を配置する。市民のWALK／WANDER、成人／子供と、上段から戻るための階段も含む。

開始位置は`(-5,83,-5)`。スマートフォン・レンチ・市民スポーンエッグ・クリエイティブモーター・ベルトコネクターを所持する。スマートフォンで経路を確認し、モーターのGUIから速度0／反転を試せる。準備完了のログは`ESCALATED_MANUAL_WORLD_READY`、初期画像は専用環境の`escalated-manual-ready.png`。

6人のWALK市民は到着Markerの変化を`ESCALATED_MANUAL_PATROL`へ記録する。開始Markerを含む4回の認識で`ESCALATED_MANUAL_PATROL_PROVED`を出し、各コースの連続3区間の移動を追跡する。これはサーバー側の到着追跡であり、ユーザーの目視確認やWANDERの全挙動の合格を自動判定するものではない。

### 移動失敗の回帰

```powershell
.\gradlew.bat runRouteTestServer -PjeiEnabled=false '-ProuteTests=isolatedMarkersDoNotDivertDestinationTravel,realGaugeGatewayTransitKeepsMembershipUntilTheFinalDistrictArrival,unreachableMarkerEntersLowFrequencyRetryAndRecovers,shortRecoveryWalkClearsPreviousNavigationFailures,districtWanderersFillOnlyAvailableRouteDensityAndProtectFlags' --offline
```

接続EdgeのないMarkerを強制経由せず、遠い目的地への自由区間を継続できることを専用試験で確認する。地区転送は地区内にある有効なGatewayを使い、700tick期限と最終到着まで所属を変えない期待値を維持する。旧`gatewayDiagnosticFar`はGatewayを地区範囲外へ置いていたため廃止した。`GAUGE_GATEWAY_ROUTE_DIAGNOSTIC`に50／150／300／500／650tickのMarker・経路を記録する。

復旧2件は650tickで頭上の障害物を除去し、1180tickまでに実歩行で到着して失敗回数を0へ戻す。短距離ケースはRegionへの進入まで0.5block未満であることを検査し、両方とも復旧Navigationが1回で完了することを要求する。`ROUTE_RECOVERY_TRACE`には状態変更時の開始位置・Path発行状態を記録し、正常な復旧中に過去の失敗を理由とした中断がないか追跡できる。

## 古布リサイクル・被服職人

`OldClothRecipeTest`はCrushingの生成JSON、追加式の職業POIタグ、アイテムモデル、日英翻訳と通常／ゾンビ村人の64×64職業テクスチャを検査する。`TailorGameTests`は全Garmentと金属装飾品のタグ区分、NBT非依存の破砕、実Create Crusherの入出力、全ミシンStateのPOI、無職成人のバニラ就職AI、各レベル2枠の候補・数量・抽選、達人10オファーと補充・保存復元を検証する。古布2個→エメラルド1個の実オファーと全縫製レシピから、基本価格での素材買戻し／購入材料の縫製・破砕が利益を生まないことも検査する。

`runJeiTestClient`はCreate標準Crushingカテゴリの入力／出力逆引きとGarment NBT、古布のモデルBake、日英の職業名とバニラ取引画面、通常村人／ゾンビ村人の描画を検査する。画像は`build/phase23/screenshots/tailor-{en,ja}.png`、`tailor-trades-{en,ja}.png`、`old-cloth-crushing-{en,ja}.png`。成功印は`TAILOR_JEI_CLIENT_PASSED`と全体の`PHASE23_JEI_CLIENT_PASSED`。

`GarmentItemDisplayTest`は全衣服の生成モデルに古布の`particle`指定があることを検査する。Clientでは全Garmentの通常／プリセットNBTについて、Bake後のSpriteとバニラ`BreakingItemParticle`の実Quad UVが古布のAtlas領域を参照することも検証する。

`necklace`と`bracelet`（`MetalAccessoryItem`）、`glasses`・`sunglasses`・`watch`はリサイクル対象外。JUnitで生成タグからの除外を確認し、GameTestで通常品／プリセットNBTの破砕拒否、Clientで同じNBT別のJEIリサイクル逆引き除外を確認する。

`ItemGuideTooltipChecks`は全衣服の通常品／プリセットNBT・通常／詳細Tooltipを日英で検査し、ファッションタグ行がなく、装備スロットと素材情報が残ることを確認する。内部のタグ解決は既存`FashionTagTest`で引き続き検証する。

```powershell
.\gradlew.bat -PjeiEnabled=false runData
.\gradlew.bat -PjeiEnabled=false build runGameTestServer
.\gradlew.bat runJeiTestClient
```

生成直後のResource更新を確実に反映するため、`runData`の完了後に次のGradleを実行する。実行中のMinecraftと同じ`build/resources`を更新する別のGradle処理を並行起動しない。

移動再開の`phoneAndCameraPauseTheSameIntentAndResumeAfterClosing`は、床への実際の接地を待ってから会話を始める。復旧の`stuckWalkerWithNoUsableMarkerResumesWanderWithoutWarp`は128block上の部屋・地区・Marker・Citizenを使い、32block以内の他FixtureのMarkerから分離する。両試験はMarker混入を前提検査で拒否し、元の期限・移動距離・会話後20tickの判定を維持する。

## Intentの経路優先移動

`CitizenIntentRouteGameTests`は5種類のIntentがSTANDのまま接続Graphを実際に歩き、利用可能なGatewayを先行利用しないことを検査する。始点側だけ／終点側だけのGraph、部分経路cacheの編集無効化、経路→自由Navigation、自由Navigation→経路、切れた2つの経路への再接続・往復ループ防止と、既存Graph予算の維持も専用Dedicated GameTestで検証する。通常行動のWALK化で代替しない。

## Fashion / Retail / Advertisement

広告矩形の回帰は`FashionRetailGameTests`の`bothPanelTypesPreserveRectanglesAndRejoinAfterRefillingAHole`、`panelPlacementTicksGrowRectanglesAndBuildIndependentAdjacentRectangles`、`panelMergeLimitsDoNotPreventANewAdjacentRectangle`、`panelMultiwayRectanglesAndEditedFragmentsRespectTheirMergeFamilies`、`signageDeferredRemovalRecoversChannelAndTheRemainingRectangle`で検査する。両媒体・全方位の破壊後の矩形維持と穴埋め、実予約Tickでの隣接拡張・別矩形の形成、枚数上限・所有者・個別設定の保護、多矩形の一括連結、Familyの保存、代表不在のサイネージ破壊とChannel／Layout保持を含む。

`FashionTagTest`は補正順、TARGET適格性、CLIMATE変更拒否、再読込相当の置換と全同梱データを検証する。`src/test/resources/garment/fashion-tag-combinations.tsv`は仕様資料の全115衣服・516組から独立に転記した期待値であり、実装から生成しない。全許可Designとの過不足、最終タグの完全一致、Definitionの通信往復後も同じ解決結果になることを確認する。資料のタグを変更した場合は期待値も更新する。`revisedFashionTagsLoadOnTheServerAndKeepDesignSpecificEligibility`は実Serverのリソース読込と、Creeper柄TシャツのAdult適格性・他衣服のkids維持・靴下とセーターの補正差などを検査する。`PhotoLayoutMathTest`はFIT/FILLの縦横比と位置寄せを検証する。

写真ベースのデフォルト広告と最大9シーンの配信は`AdvertisementScenesTest`で、3種の同梱SNBT・9シーンの順序／ループ／クロスフェード境界・Packet往復と件数上限・デフォルト媒体のLayout保存を検査する。`FashionRetailGameTests`の`defaultAdvertisementsUseWorldPhotosAndPostersDoNotOpenMenus`、`broadcasterStoresNinePhotosPublishesOnCloseAndReturnsItems`、`panelLayoutsAreStagedAndSignageSceneEditsDoNotRewriteBroadcasts`、`legacyAdvertisementChannelsAndUnconfiguredGlobalPanelsMigrate`で、ポスター非GUI・発光15・配信機の9実物枠と疎な同一写真のNBT保存・閉鎖時確定・権限／旧Menu拒否・破壊時返却・配信データ保持・旧単独シーン／global移行を検査する。Retailの警告は全シーンの可視衣服を確認する。

Fashion Client内の`AdvertisementSceneClientChecks`は、看板の3×2比率プレビューと6設定のみの画面、サイネージの2×3比率・チャンネル・9シーン選択、配信機の3×3枠への実SHIFT移動、チャンネル入力でのEキー、閉鎖時保存、公開写真非変更とJEI非表示を検査する。画像は`build/fashion-billboard-settings.png`、`fashion-signage-scene-settings.png`、`fashion-broadcaster-nine-scenes.png`、`fashion-signage-crossfade.png`。写真編集はインベントリ／衣服枠を持たない`PhotoEditScreen`の6設定＋保存だけとし、`fashion-photo-edit-fixed-direction.png`へ撮影する。

看板／サイネージの128×128アイテム画像を再生成する場合は、次の順に実行する。Native側で同梱写真1／2を実レンダーし、広告部分だけ14×14へ間引いて8倍の最近傍拡大にする。枠は従来の解像度を保ち、`src/generated/resources/assets/createapparel/textures/item/`の2画像へ出力する。`FashionResourceTest`は広告部分の8×8単色セルと枠の色も検査する。続くDatagenが描画済みPNGをCacheへ登録し、生成モデルと翻訳も更新する。通常のDatagenは同梱済み生成PNGを保持するが、PNGを手動で削除した場合は先にNative生成が必要。

```powershell
.\gradlew.bat runFashionTestClient -PgenerateAdvertisementIcons=true
.\gradlew.bat runData
.\gradlew.bat build runGameTestServer
```

元ワールドの写真データを調査する場合は`src/test/tools/read_barrel_photos.py <world-folder>`をPython 3で実行する。Anvil/NBTを標準ライブラリのみで読み、樽の座標・Slotと写真SNBTを標準出力へ出す。ワールドを書き換えない。

広告媒体の回帰は`FashionRetailGameTests`で、撮影時の身体の向きが編集操作・直接Data変更でも維持されること、上下ポスターの全方位の設置・支持喪失・単一ドロップ、看板の矩形再編成と最後の1枚だけ写真入りの回収、代表不在時の破壊記録・複数回破壊後の遅延復元を検査する。印刷は実DeployerのRecipe検索とDepotの処理・Capabilityを使い、1媒体だけの変換・写真Stackと追加データの非消費・不正入力／再印刷拒否・Serializer同期を確認する。`FashionResourceTest`はポスターの厚さ・絵画背面と2つのDatagen印刷レシピも検査する。

JEI有効の`runFashionTestClient`はCreate標準デプロイヤーでの写真U検索／媒体R・U検索、完成品の写真入り見本と光沢、元Recipeの不変性、媒体の説明ページと手動印刷の非登録を検査する。写真編集画面の6設定＋保存・向き設定の非表示、向き操作Packet拒否とFraming変更の保存を確認し、`build/fashion-photo-edit-fixed-direction.png`を出力する。`runData`で新規レシピを生成した後は、別のGradle呼び出しで`build`／GameTest／Clientを実行し、先にコピーされた旧リソースでの試験を避ける。

カメラ取引は`FashionRetailGameTests`で、入力支払い・未払いプレビュー非表示・通常／Shift取得・満杯／カーソル占有／不正Clickの無課金拒否・終了時の返却・旧撮影ボタンの無効化・衣服欄の共有配置・双方向の設定変更・身体の向きの保存／旧写真互換・見積り失効を検査する。`PhotoLayoutMathTest`は角度の範囲も確認する。専用Fashion Clientは色グリッド・ホイールでの向き変更・実スロットPacketでの投入／取得・料金差分・取引成功音を確認し、JEI有効時は実Runtimeの画面判定と一覧非表示も検査する。画像は`fashion-camera-{color-selector,paid-preview}.png`と`fashion-photo-rotated.png`。

`PhotographyActionPacketTest`は全設定・色・逆スクロール・Garment選択・スタイルカテゴリの操作番号をVanillaのsigned byte Packetで往復する。`PhotographyStylesPacketTest`はMenu ID・写真UUID・全Garmentスロットのスタイル同期を確認する。`photographyStylesAreTemporaryAndCapturedInThePhoto`は一時スタイルの双方向変更・不正カテゴリ／古いMenuの拒否・写真への保存・実物非変更・次回撮影での復元を検査する。専用Clientは実ボタンとホイールでスタイル・Garment選択を変更し、写真への保存を検査して`build/fashion-camera-style-selector.png`へ撮影する。カメラClient試験も逆順→順順の実Packetを送り、15度になることを確認する。料金を64個超に設定した場合の複数スタックの預かり・同一NBT返却・確定消費はGameTestで検査する。

`photographyStartsWithTheModelsWornStyles`は、市民と自撮りの現在のタックイン・袖まくり・前開きが撮影開始時のスナップショット・Menu・未変更の写真に引き継がれ、モデルの実装備が変わらないことを確認する。Client QAは同じ非既定スタイルのモデルで画面を開き、Vanillaの初回スロット同期後にも状態が維持されることを検査して`build/fashion-camera-initial-worn-styles.png`へ撮影する。

カメラ衣服欄の一時的な着脱・交換・回収は`photographyClothingDragAndDropIsTemporaryAndPreservesPaidPhotos`、VanillaのQUICK_CRAFT開始／対象／終了通知は`photographyQuickCraftCanDressAndRecoverTrialClothing`、画面外投棄・Q・複製・ホットバー交換・死亡時のコピー流出防止、実物枠の保護と外部の所持品更新は`photographyTrialClothingCannotEscapeAndTracksExternalInventoryChanges`で検査する。元の衣服とモデルの実装備が変わらず、閉鎖時に写真や支払いを巻き戻さないことも確認する。専用Clientは実GUIのマウス操作で衣服回収→試着→返却→元の服へ戻す操作を行い、`build/fashion-camera-trial-clothes.png`を保存する。

`photographyTrialClothingCannotEnterOrExtractRealBundleContents`は、衣服入りBundleの右クリック／右ドラッグによる実物の展開と、空のBundleへの仮コピー収納を拒否し、閉鎖後の実物と収納内容が完全に一致することを検査する。

`PhotographyMenuLayoutTest`は320×240の最小GUI領域、衣服・プレビュー・スタイル・撮影設定の4列が重ならないこと、18px高のボタン、衣服領域内のスロット中央配置、18pxへ詰めた下段の間隔、返却注意書きの余白、インベントリと取引出力枠の等しい外側余白、作業台と同じ58pxの入力／出力間隔を検査する。`PhotoLayoutMathTest`は新規撮影の背景が白で、保存済みの背景色は読み込み時に維持されることも確認する。`photographyAndEditingStayOpenWhileOnlyConsentExpires`は共有時計を変えずにメニューの期限を過去へ設定し、撮影・編集は継続できて承認だけ失効することを確認する。専用Clientは新規撮影の白背景と衣服スロットの位置を確認し、960×720／GUI Scale3でも画面に収まる画像を`build/fashion-camera-compact.png`へ保存する。写真取得時の経験値オーブ取得音を実Sound Eventで検査する。

`FashionResourceTest`は作成済み5種のモデル／BlockStateのmain管理、生成先との非重複、写真／ポスターのアイコン、配信機の面別テクスチャとbottom=top、旧広告PNGの不在、看板／サイネージの生成アイコンとモデル参照を確認する。`ResourceIntegrityTest`と合わせて参照切れ・重複も検査する。

共通GameTestの`GarmentCreativePresetsGameTests`はメインタブの実表示順（先頭の道具、鏡の後の店舗／広告媒体、末尾の写真用品とスポーンエッグ）と衣類タブの独立した外観Stackを検証する。

共通GameTestの`FashionRetailGameTests`は実Registry・Storage・Create Seatを使い、撮影料金・無課金失敗・購入時の1着減算と購入元ID、矩形連結と分割、ポスター片側破壊、同一NBTの写真2枚複製／デプロイヤー印刷時の写真非消費、通常／Shiftクラフトと満杯失敗、配信機4方向、共有チャンネル、市民との会話停止／閉鎖後の実歩行再開と単独セッションを確認する。媒体の製作は紙4枚→ポスター1個と、ポスター1個＋ガラス板1枚→看板パネル2個を実2×2 Gridで検証し、写真と媒体をクラフトしても印刷できないことを確認する。

同一Citizenのスマートフォン／カメラ共通の一人専有は`phoneAndCameraShareOneExclusiveCitizenSession`で全4組合せを検査する。`invalidCitizenSessionsReleaseForDisconnectReplacementAndDimensionChange`と`exclusiveCitizenInteractionReleasesInvalidOwners`は閉鎖・切断・死亡・距離・Dimension・Menu差替え後の解放、`directCameraMenusCannotBypassAnOccupiedCitizenOrCharge`は不正な直接Menu生成の拒否／無課金と別Citizenの独立性、`replacingOwnCitizenMenuKeepsTheNewMenuExclusive`は同じ本人の切替と遅延終了通知の無効化を検査する。`CitizenMenuGameTests`の`exclusiveMenusRejectOtherPlayersAndPhoneRequiresActualHand`と`CitizenBehaviorGameTests`の`poseEditingIsExclusiveAndHandoverUsesTheNewSessionBase`も、衣服／見た目／行動／向きへの他人の変更拒否と引継ぎを確認する。

`FashionObservationGameTests`はマネキンと広告3種の実Sourceを使い、通常／色付き／遮光ガラス・板ガラス・多重ガラス越しの観察、ガラス奥の壁・鉄格子による遮蔽、背後の観察拒否、他市民の視認条件の維持を確認する。未ロードChunkを視線判定で強制ロードしないことも検査する。観察済み履歴で判定し、購入抽選の成功率には依存しない。

`RetailSetupGameTests`は正面シートの4方向・同高／1段下と範囲外拒否、通常使用でのスマートフォン選択、在庫の色一致と不足・補充、サイネージ配信更新、索引の解除・撤去、広告で見えない衣服とレジ再設置の誤警告防止を検査する。1024スロットの試験は1巡回256スロットの予算、90tick以上無変更なら再走査しないキャッシュ、実extract／insertの通知による再照合、接続数、simulateのみで在庫を消費しないことも確認する。

同クラスは、同一tickの選択スロット往復Packet・同一内容のスマートフォン再同期の維持・異なるデータのスマートフォンへの交換・副手での解除、チェストの結合／片側破壊／保存復元／同座標の別ブロック拒否／結合リンク重複除去、Create保管庫のX／Z方向の幅・長さ・代表変更と構成在庫の変更通知も検査する。縦型の共通APIはY軸のテスト専用`Inventory`実装で検査し、特定アドオン実MODでの互換試験とは区別する。

リンク操作の試験は、空手のレジ使用でメッセージがないこと、在庫・マネキン・広告3種でリンク／解除／再リンクと各1回の成功通知が一致すること、連結広告の非代表パネルから全体を解除できること、展示物索引からも解除されることを確認する。`ProductionGuideTest`は日英の明示的なリンク／解除文と旧通常操作メッセージの撤去を検査する。

```powershell
.\gradlew.bat -PjeiEnabled=false test --tests '*FashionTagTest' --tests '*PhotoLayoutMathTest'
.\gradlew.bat -PjeiEnabled=false test --tests '*FashionResourceTest' --tests '*ResourceIntegrityTest'
.\gradlew.bat -PjeiEnabled=false runGameTestServer
.\gradlew.bat -PjeiEnabled=false runFashionTestClient
```

専用Clientは`src/test/fashion/`、設定は`src/test/fashion-tests.gradle`、隔離Worldは`run/fashion-tests/client/`。撮影GUI、縦横広告、撮影元消滅後の表示、3媒体、Channel差分同期、Player Skinのフォールバック、Resource Reloadを実行する。作成済み5種をHotbarへ並べてモデル欠落を確認し、レジと配信機も設置する。市民の服装ロックは18pxの正方形位置とTooltip、解除／ロックの実同期と画像を確認する。成功印は`FASHION_CLIENT_QA_SUCCESS`、画像は`build/fashion-*.png`。

独立2Client間の承認操作、外部アカウントの実スキン変更、VS2実MOD、大量広告のMSPT/FPSベンチマークをこの専用試験で代替しない。

同Clientの`RetailClientChecks`は実NetworkとゴーグルOverlayを使い、開店／閉店・店員／在庫状態と接続数、変更通知による在庫不足と補充後の解除、主手／副手のスマートフォンでの4種類のリンク枠、道具解除後の枠消去、Shift詳細の撤去、店舗Ponderの再生・リプレイを検査する。Ponderは6説明への短縮と、村人が実SeatEntityに乗車してCreateの乗員位置にいることを検査する。日英のGoggles／Ponder画像は`build/retail-*.png`、着席画像は`retail-ponder-cashier.png`、成功印は`RETAIL_CLIENT_QA_SUCCESS`（全体完了は`FASHION_CLIENT_QA_SUCCESS`）。

同検証の末尾で、ラージチェスト化の構成同期・全体枠・接続数1、元側の破壊後の残存チェスト枠、実選択スロットPacketによる設定解除も検査する。画像は`build/retail-large-chest-highlight.png`と`build/retail-surviving-chest-highlight.png`。

設定中の対象注視は、実カーソルと開始／解除のNetwork同期でラージチェスト・非代表広告パネル・マネキンを検査し、未リンク対象・リンク解除・設定終了・画面表示・副手・スロット往復による表示切替を確認する。画像は`build/retail-setting-{storage,advertisement,mannequin}.png`。`RetailLinkSessionPacketTest`はレジ参照・両手・9スロットの往復、1byteの解除通知、不正スロットの拒否を検査する。

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

1.21.1ではGameTest構造物が自動的にBarrierで囲まれる。既存`apparel_state`は外側の空間も使うfixtureのため、16×8×16のロード範囲を予約し、テスト開始時にそのBarrierだけを除去する。Citizen用の明示的な部屋・壁・障害物は保持する。`-PgameTests=<methodName,...>`で共通GameTestを選択できる。

Minecraft 1.21.1 / NeoForge 21.1.265 / Java 21を使用する。PowerShellでは事前にJava 21の`JAVA_HOME`を設定する。
化学連携APIのコンパイル用Destroy・PetrolparkはGradleが取得する。
これらは`compileOnly`の開発依存で、明示的に有効化しない限り通常Client／Serverのruntimeや配布JARには含めない。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12.1'
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

現行の機能・予算・LODを維持する性能確認は、次を別々に実行する。描画／MSPTを比較する間は他のMinecraft検証を同時起動しない。

```powershell
.\gradlew.bat --offline -PjeiEnabled=false -PgaugeScale=true -PgaugeScaleProfile=true runGaugeTestServer
.\gradlew.bat --offline -PjeiEnabled=false -PgaugePerformance=true -PgaugePerformanceCount=0 -PgaugePerformanceProfile=false -PgaugePerformanceSteady=true runGaugeTestServer
.\gradlew.bat --offline -PjeiEnabled=false -PgaugePerformance=true -PgaugePerformanceCount=1000 -PgaugePerformanceProfile=false -PgaugePerformanceSteady=true runGaugeTestServer
.\gradlew.bat --offline -PjeiEnabled=false -PgaugeRenderClient=true runGaugeTestClient
.\gradlew.bat --offline -PjeiEnabled=false -PgaugeDisplayPerformance=true runGaugeTestClient
```

実市民のCPU試験は実時間10秒以上のwarmup後、400tickのMSPTを出力する。既存のACTIVE／THROTTLEDと探索予算を維持し、各停止診断フラグは指定しない。保存規模の試験とは実Entity数が異なる。

`DisplayPerformanceClientChecks`は新規の隔離Worldで、各種類0→32→128個のMannequin／Wall Hanger／Rackを配置する。Rackは各2着、他は各1着。各段階の準備後100Client tickを待ち、1280×720・VSyncなし・FPS設定260（無制限）で240frameを測り、平均／p95 frame時間、ロード済みMannequin／BE数、製品RendererにSnapshotが作られたMannequin数、合成Texture数、Integrated Server平均MSPTを`DISPLAY_PERFORMANCE`へ出す。Snapshotの照会は測定後に一度だけ行い、毎frameのdraw数とは区別する。定常frameで新しい合成Textureが作られないことも要求する。数値を速度の合否閾値には使わない。画像は`build/display-performance-{0,32,128}.png`、成功印は`DISPLAY_PERFORMANCE_CLIENT_PASSED`。

経路View通信の単独編集試験は、Chunk準備40tick後に先行QAの残存Citizenと一時的な実行状態を片付ける。測定は従来の240tick、16人、1編集の16Packetとpayload比率を維持する。空Deltaが追加された場合は`ROUTE_VIEW_DELTA`のruntime／load／maintenance Revisionで原因を区別する。

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

Trash Cans実MODのQAは`.\gradlew.bat -PjeiEnabled=false -PgaugeTrash=true runGaugeTestServer`を使う。以下のNeoForge版を公式Modrinthからテストruntimeへ取得し、通常のruntime・配布依存には追加しない。

- Trash Cans 1.1.2 / NeoForge（TbzPls06）
- SuperMartijn642's Core Lib 1.1.26 / NeoForge（HGyYs7Z4）
- SuperMartijn642's Config Lib 1.1.8 / NeoForge（qKL9jM75）

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
`runJeiTestClient`は広告写真複製の標準Crafting登録、未設定／撮影済み写真のR／U検索、入力1枚・同一NBT出力2枚、検索元の不変性、Datapackによる削除・追加後の再登録も検証する。`build/phase23/screenshots/photo-copy-en.png`／`photo-copy-ja.png`に実画面を保存する。
同Client QAの`ItemGuideTooltipChecks`は対象15種の通常Tooltip、材料・製品の黄色、家具説明のオレンジと通常時のShift案内より前の配置、Shift中は内部へ移動して1回だけ表示すること、ゲージ概要と記録座標を検査する。家具のShift表示はCreateの`linesOnShift`と既存BlockItem説明を合成して配置処理を検証する。日英の通常表示は`build/phase23/screenshots/tooltips-{1,2}-{en,ja}.png`、家具のShift表示は`tooltips-shift-{wardrobe,hanger,rack}-{en,ja}.png`に保存する。
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

`-PdestroyEnabled=true`で[NHblock714/Destroy fork 0.4.4](https://github.com/NHblock714/Destroy/releases/tag/v0.4.4)とPetrolpark's Library 1.5.0を取得する。銀ありの専用テストには[The Silver Age 1.21.1 / 1.2.0](https://modrinth.com/mod/the-silver-age/version/zKe6RVwW)を追加する。銀MODはテストruntime限定で、Create: Apparelの配布物や必須依存には含めない。旧Forge用JARの手動配置は不要。

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
