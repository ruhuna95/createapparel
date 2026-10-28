package com.rexhub.createapparel.data.client;

import com.rexhub.createapparel.guide.ItemGuide;
import java.util.function.BiConsumer;

final class ItemGuideLanguage {
    private ItemGuideLanguage() { }

    static void add(BiConsumer<String, String> add, boolean japanese) {
        for (var row : new Object[][]{
                {ItemGuide.SPINNING, "Spins _fibers_, _wool_ or _cocoons_ into _thread_.", "_繊維_・_羊毛_・_繭_から_糸_を作る。"},
                {ItemGuide.LOOM, "Weaves four matching _threads_ into undyed _fabric_.", "同じ素材の_糸_4個から未染色の_生地_を織る。"},
                {ItemGuide.SEWING, "Sews _fabric_ and _thread_ into _garments_ using a configured pattern.", "型紙に合わせて_生地_と_糸_から_衣服_を縫う。"},
                {ItemGuide.PATTERN, "Sets the garment, design and additional colors for sewing.", "縫う衣服・デザイン・追加色を指定する型紙。"},
                {ItemGuide.PHONE, "Manages Citizens and edits the Route Graph.", "市民の管理と経路グラフの編集に使用。"},
                {ItemGuide.GAUGE, "Manages District population, transfers, influx and outflow through linked Gauges.", "接続したゲージで地区の人口・移動・流入・流出を管理する。"},
                {ItemGuide.WARDROBE, "Can change clothes.", "衣服を着替えることができる。"},
                {ItemGuide.HANGER, "Can hold tops and outerwear.", "トップスとアウターを掛けることができる。"},
                {ItemGuide.CLIP_HANGER, "Can hold bottoms.", "ボトムスを掛けることができる。"},
                {ItemGuide.RACK, "Can hold tops and outerwear.", "トップスとアウターを掛けることができる。"},
                {ItemGuide.CLIP_RACK, "Can hold bottoms.", "ボトムスを掛けることができる。"},
                {ItemGuide.MANNEQUIN, "Displays apparel on the body parts supported by this mannequin.", "対応する部位に衣服を装着して展示する。"},
                {ItemGuide.GARMENT, "Clothing that players and Citizens can wear.", "プレイヤーや市民が着ることが出来る衣服。"},
                {ItemGuide.MIRROR, "Reflects nearby surroundings and worn apparel.", "周囲の景色や着用した衣服を映す鏡。"},
        }) {
            var guide = (ItemGuide) row[0];
            if (!guide.hasSummary()) throw new IllegalStateException("Unexpected item guide: " + guide);
            add.accept(guide.translationBase() + ".tooltip.summary", (String) row[japanese ? 2 : 1]);
        }
        add.accept("guide.createapparel.phone.tooltip.short_summary",
                japanese ? "市民や地区の管理に使用。" : "Used to manage Citizens and Districts.");
        FashionLanguage.guides(add, japanese);
        details(add, japanese, ItemGuide.PATTERN, new String[][]{
                {"When used in hand", "手に持って右クリックしたとき", "Choose the garment, design and additional colors. Changes are saved automatically.", "衣服・デザイン・追加色を選ぶ。変更は自動保存される。"},
                {"When used on the machine's filter", "ミシンのフィルター欄に使ったとき", "Sets the sewing recipe. The preview's fabric color is not saved to the pattern.", "縫製レシピを指定する。プレビューの生地色は型紙へ保存しない。"}});
        details(add, japanese, ItemGuide.PHONE, new String[][]{
                {"When used on an entity", "対象を右クリック", "Villager: convert. Citizen: settings.", "村人：市民へ変換。市民：設定画面。"},
                {"When held", "経路の可視化と編集", "Either hand: view routes. Main hand: edit.", "両手とも経路を表示。編集はメインハンドのみ。"},
                {"When sneak-using ground", "地面上面をスニーク右クリック", "Place a Marker above solid, dry ground in empty space.", "液体のない地面の上側の空間にマーカーを設置。"},
                {"When right-clicking Markers", "マーカーを右クリック", "Two Markers: link both ways. Same Marker: cancel.", "2点を選ぶと双方向接続。始点を再選択で解除。"},
                {"When sneak-right-clicking", "スニーク右クリック", "Marker: settings/delete. Targeted Edge: delete, if editable.", "マーカー：設定・削除。照準中の接続：編集可能なら削除。"},
                {"Route display", "経路の表示", "Dashed: slate = unloaded, purple = failed, orange = removed (10s). Gateway/Crossing: Marker settings.", "破線：灰青＝未ロード、紫＝失敗、橙＝切断跡（10秒）。ゲートウェイ・横断の条件はマーカー画面。"}});
        details(add, japanese, ItemGuide.WARDROBE, new String[][]{
                {"When right-clicked", "右クリックしたとき", "Open the apparel screen. Move clothes between inventory and equipment slots, then adjust appearance.", "衣服画面を開く。持ち物と装備スロットの間で衣服を移動し、見た目を調整する。"}});
        details(add, japanese, ItemGuide.GAUGE, new String[][]{
                {"When recording a District", "地区を登録するとき", "Sneak-use the top of the ground to reserve a center, then place on a wall. Recorded or used centers cannot be recorded by another Gauge. Craft one Gauge alone to clear its recording and reservation. Up to four Gauges fit one wall block.", "地面上面をスニーク使用して中心を記録・予約し、壁に設置する。別ゲージが記録済み・使用中の地点は記録できない。ゲージ単体をクラフトすると記録と予約を解除する。壁1マスに最大4個設置できる。"},
                {"District settings", "地区ゲージの設定", "Edit the District name, radius (1-256), and target (0-radius), then save. Radius 1 covers 3x3 blocks.", "地区名・半径（1～256）・目標人口（0～半径）を編集して保存する。半径1は3×3ブロック。"},
                {"Connections and relocation", "接続と移動", "Choose Add new connection, then use another wall Gauge. Relocate this gauge keeps its District and links.", "「新しい接続を追加」から別の壁面ゲージを使用して接続する。「このゲージを移動」は地区と接続を保持する。"},
                {"Influx and outflow", "流入と流出", "Without a center, select influx or outflow in the footer and save. Influx supplies missing Citizens. Outflow prioritizes transfers and removes surplus residents only when every linked District meets its target.", "中心未設定ならグレー帯で流入／流出を切り替えて保存する。流入は不足を補充。流出は地区間移動を優先し、ネットワーク全地区が目標以上のとき余剰市民を削除する。"}});
        for (var guide : new ItemGuide[]{ItemGuide.HANGER, ItemGuide.CLIP_HANGER, ItemGuide.RACK, ItemGuide.CLIP_RACK}) {
            boolean rack = guide == ItemGuide.RACK || guide == ItemGuide.CLIP_RACK;
            details(add, japanese, guide, rack ? new String[][]{
                    {"When placing", "設置するとき", "Place on a floor or attach to a wall; extend a matching rack from its end. Sneak-place on the floor to keep it separate.", "床置き・壁付けが可能。同じ向き・設置方式の端から延長する。床へのスニーク設置では連結しない。"},
                    {"Available positions", "使用できる位置", "Floor racks have 2 positions alone, 3 at connected ends and 4 in the middle. Wall racks have 4 positions.", "床置きは単体2枠、連結端3枠、中央4枠。壁付けは4枠。"},
                    {"When right-clicking a position", "掛ける位置を右クリックしたとき", "Hold matching apparel to hang it; use an empty hand to take it. Sneaking bypasses this action.", "対応する衣服を掛け、素手なら取り出す。スニーク中は掛け外しを行わない。"}}
                    : new String[][]{
                    {"When placing", "設置するとき", "Attach to the side of a sturdy wall.", "支持できる壁面の側面に取り付ける。"},
                    {"When right-clicked", "右クリックしたとき", "Hold matching apparel to hang it; use an empty hand to take it. Sneaking bypasses this action.", "対応する衣服を掛け、素手なら取り出す。スニーク中は掛け外しを行わない。"}});
        }
        details(add, japanese, ItemGuide.MANNEQUIN, new String[][]{
                {"When holding apparel", "衣服を持って右クリックしたとき", "Equip an available slot supported by this mannequin variant.", "この種類が対応する空き部位に装着する。"},
                {"When sneak-right-clicking a body part", "部位をスニークしながら右クリックしたとき", "Remove the apparel on the targeted part.", "照準を合わせた部位の衣服を取り外す。"},
                {"When right-clicked with an empty hand", "素手で右クリックしたとき", "Open apparel settings, pose and body rotation controls.", "衣服の設定・ポーズ・本体回転を画面で調整する。"}});
    }

    private static void details(BiConsumer<String, String> add, boolean japanese, ItemGuide guide, String[][] rows) {
        for (int index = 0; index < rows.length; index++) {
            String key = guide.translationBase() + ".tooltip.";
            add.accept(key + "condition" + (index + 1), rows[index][japanese ? 1 : 0]);
            add.accept(key + "behaviour" + (index + 1), rows[index][japanese ? 3 : 2]);
        }
    }
}
