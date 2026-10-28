package com.rexhub.createapparel.data.client;

import java.util.function.BiConsumer;

public final class FashionLanguage {
    private FashionLanguage() { }
    public static void guides(BiConsumer<String, String> consumer, boolean japanese) {
        String[][] guides = {
                {"camera", "Creates reusable advertising photos of Citizens and players.", "市民やプレイヤーの広告写真を撮影する。", "Photograph a model", "モデルを撮影", "Use on a Citizen to choose clothes and pay its quoted emerald fee. Use in the air for a free self-portrait. Other players must approve.", "市民に使用して衣装を選び、表示されたエメラルドを支払う。空中使用なら自分を無料撮影。他プレイヤーは本人の承認が必要。"},
                {"photo", "An editable photo master. Clothing and body direction are fixed at capture; player skins stay current.", "編集できる写真マスター。衣服と身体の向きは撮影時に固定され、プレイヤースキンは現在のものを参照する。", "Edit and reuse", "編集と再利用", "Use to edit framing, colors and alignment. Craft one photo alone into two identical copies. Print unprinted posters or billboard panels with a Deployer holding the photo. Printing does not consume the photo.", "使用すると背景・範囲・位置寄せを編集。単体クラフトで同じ写真2枚に複製する。写真を持たせたデプロイヤーで未印刷ポスター・看板パネルに印刷する。印刷では写真を消費しない。"},
                {"advertisement", "Displays a local photo or a shared signage channel as one connected rectangular advertisement.", "個別写真または配信チャンネルを、連結した矩形広告として表示する。", "Place and configure", "設置と設定", "Poster: one block wide, two blocks tall, hung on a wall like a painting. Add blank billboard or signage panels to complete a rectangle. Removed billboard panels are blank until the final panel is collected. Sneak-place for an independent group. Link a shop with the Smart Phone.", "ポスターは横1×縦2マスで、絵画のように壁へ設置する。白紙看板・サイネージを矩形に並べて連結。看板の回収品は最後の1枚だけ写真入りになる。スニーク設置は独立。スマートフォンで購入先をリンクする。"},
                {"broadcaster", "Updates one signage channel across every dimension of this world.", "ワールド全ディメンションの同じサイネージチャンネルを一括更新する。", "Publish", "配信", "Set a channel and place up to nine photos into the grid. Close the menu to publish a fading scene loop. Empty signage loops three built-in photos. Stored photos are returned when the broadcaster breaks; published scenes remain.", "チャンネルを設定して3×3枠へ最大9枚の写真を入れ、画面を閉じるとフェード切替で繰り返し配信する。未設定のサイネージは同梱の3枚を繰り返す。配信機を壊すと保管写真は返却され、配信済みシーンは残る。"}
        };
        for (var row : guides) {
            String base = "guide.createapparel." + row[0];
            consumer.accept(base + ".tooltip.summary", row[japanese ? 2 : 1]);
            consumer.accept(base + ".tooltip.condition1", row[japanese ? 4 : 3]);
            consumer.accept(base + ".tooltip.behaviour1", row[japanese ? 6 : 5]);
        }
        consumer.accept("guide.createapparel.register.tooltip.summary",
                japanese ? "店員を配置すると在庫内の衣服を販売できる。" : "Sells garments from storage when staffed.");
        consumer.accept("guide.createapparel.photo.tooltip.short_summary",
                japanese ? "編集できる写真データ。" : "Editable photo data.");
        for (var row : new String[][]{
                {"poster", "A poster for fashion advertisements.", "ファッション広告のポスター。"},
                {"billboard_panel", "A billboard panel for fashion advertisements.", "ファッション広告の看板パネル。"},
                {"signage_panel", "Digital signage for fashion advertisements.", "ファッション広告のデジタルサイネージ。"}
        }) {
            consumer.accept("block.createapparel." + row[0] + ".tooltip.summary", row[japanese ? 2 : 1]);
        }
    }
    public static void add(BiConsumer<String, String> consumer, boolean japanese) {
        String[][] entries = {
                {"block.createapparel.cash_register", "Cash Register", "レジ"},
                {"block.createapparel.poster", "Fashion Poster", "ファッションポスター"},
                {"block.createapparel.billboard_panel", "Billboard Panel", "広告看板パネル"},
                {"block.createapparel.signage_panel", "Digital Signage Panel", "サイネージパネル"},
                {"block.createapparel.signage_broadcaster", "Signage Broadcaster", "サイネージ配信機"},
                {"item.createapparel.camera", "Camera", "カメラ"},
                {"item.createapparel.advertising_photo", "Advertising Photo", "広告写真"},
                {"jei.createapparel.photo_copy.input", "Use one captured photo. Player crafting only.", "撮影済みの写真1枚を使用。プレイヤーのクラフトのみ対応。"},
                {"jei.createapparel.photo_copy.output", "Both copies retain all original photo data.", "写真データが完全に同じ写真2枚になります。"},
                {"tooltip.createapparel.garment.fashion_tags", "Fashion: %s", "ファッション: %s"},
                {"screen.createapparel.fashion.lock", "Outfit lock", "服装ロック"},
                {"tooltip.createapparel.register.open", "OPEN", "開店中"},
                {"tooltip.createapparel.register.closed", "CLOSED", "閉店中"},
                {"tooltip.createapparel.register.cashier", "Cashier: %s", "店員：%s"},
                {"tooltip.createapparel.register.present", "Present", "在中"},
                {"tooltip.createapparel.register.absent", "Absent", "不在"},
                {"tooltip.createapparel.register.storage", "Storage: %s", "在庫：%s"},
                {"tooltip.createapparel.register.connected", "Connected (%s)", "接続中（%s個）"},
                {"tooltip.createapparel.register.disconnected", "Disconnected", "未接続"},
                {"tooltip.createapparel.register.missing_stock", "Warning: displayed clothing is missing from stock", "警告：展示中の衣服が在庫にありません"},
                {"tooltip.createapparel.register.checking", "Checking shop information...", "店舗情報を確認中…"},
                {"message.createapparel.register.linked", "Linked to the register.", "レジとリンクしました"},
                {"message.createapparel.register.unlinked", "Unlinked from the register.", "レジとのリンクを解除しました"},
                {"message.createapparel.register.link_selected", "Register selected. Sneak-use storage, a mannequin or a screen; select this register again to finish", "レジを選択しました。在庫・マネキン・広告面をスニーク使用でリンク。同じレジを再選択すると終了"},
                {"message.createapparel.register.link_cleared", "Register link selection cleared", "レジのリンク操作を終了しました"},
                {"message.createapparel.register.storage_unavailable", "Cannot link this storage: check distance (64 blocks), inventory access and the %s-slot limit", "在庫をリンクできません。距離64ブロック以内・在庫機能・%sスロット上限を確認してください"},
                {"message.createapparel.photo.failed", "Photography failed: check the quote, emeralds, inventory space and model distance", "撮影できません。見積り、エメラルド、空きスロット、モデルとの距離を確認してください"},
                {"screen.createapparel.photo.capture", "Model photography", "モデル撮影"},
                {"screen.createapparel.photo.return_clothes", "Clothing is returned when you close this menu.", "※メニューを閉じたら衣服は返却されます"},
                {"screen.createapparel.photo.edit", "Edit advertising photo", "広告写真を編集"},
                {"screen.createapparel.photo.consent", "Photography request", "撮影の承認"},
                {"screen.createapparel.photo.consent_hint", "A nearby player wants to photograph you. Your outfit and pose are captured; your player skin stays current.", "近くのプレイヤーから撮影依頼が届きました。衣服とポーズは撮影時に固定され、スキンは現在のものを参照します。"},
                {"screen.createapparel.photo.background", "Background: %s", "背景: %s"},
                {"screen.createapparel.photo.framing", "Framing: %s", "範囲: %s"},
                {"screen.createapparel.photo.aspect", "Fit: %s", "表示: %s"},
                {"screen.createapparel.photo.horizontal", "Horizontal: %s", "横寄せ: %s"},
                {"screen.createapparel.photo.vertical", "Vertical: %s", "縦寄せ: %s"},
                {"screen.createapparel.photo.mirror", "Mirror: %s", "左右反転: %s"},
                {"screen.createapparel.photo.pose", "Pose: %s", "ポーズ: %s"},
                {"screen.createapparel.photo.body_yaw", "Body direction: %s", "身体の向き: %s"},
                {"screen.createapparel.photo.save", "Save", "保存"},
                {"screen.createapparel.photo.approve", "Approve", "承認"},
                {"screen.createapparel.photo.value.full_body", "Full body", "全身"},
                {"screen.createapparel.photo.value.upper_body", "Upper body", "上半身"},
                {"screen.createapparel.photo.value.close_up", "Close-up", "顔"},
                {"screen.createapparel.photo.value.fit", "Fit", "全体を収める"},
                {"screen.createapparel.photo.value.fill", "Fill", "面を満たす"},
                {"screen.createapparel.photo.value.left", "Left", "左"},
                {"screen.createapparel.photo.value.center", "Center", "中央"},
                {"screen.createapparel.photo.value.right", "Right", "右"},
                {"screen.createapparel.photo.value.up", "Top", "上"},
                {"screen.createapparel.photo.value.down", "Bottom", "下"},
                {"screen.createapparel.advertisement.control", "Advertisement settings", "広告の設定"},
                {"screen.createapparel.advertisement.channel", "Channel (a-z, 0-9, _)", "チャンネル（英小文字・数字・_）"},
                {"screen.createapparel.advertisement.scene", "Scene: %s / %s", "シーン: %s / %s"},
                {"message.createapparel.advertisement.channel_denied", "You cannot update this channel.", "このチャンネルを更新する権限がありません。"}
        };
        for (var entry : entries) consumer.accept(entry[0], entry[japanese ? 2 : 1]);
        String[] en = {"Cool", "Cute", "Simple", "Flashy", "Casual", "Formal", "Feminine", "Masculine", "Kids", "Thin", "Thick"};
        String[] ja = {"かっこいい", "かわいい", "シンプル", "派手", "ラフ", "フォーマル", "女性服", "男性服", "子供服", "薄着", "厚着"};
        for (var tag : com.rexhub.createapparel.fashion.FashionTag.values()) consumer.accept("fashion_tag.createapparel." + tag.id(), (japanese ? ja : en)[tag.ordinal()]);
    }
}
