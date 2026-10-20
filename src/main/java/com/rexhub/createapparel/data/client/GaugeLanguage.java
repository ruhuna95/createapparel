package com.rexhub.createapparel.data.client;

import java.util.function.BiConsumer;

final class GaugeLanguage {
    private GaugeLanguage() { }
    static void add(BiConsumer<String, String> add, boolean japanese) {
        add.accept("block.createapparel.citizen_gauge", japanese ? "市民ゲージ" : "Citizen Gauge");
        String[][] entries = {
                {"district", "地区ゲージ", "District Gauge"}, {"source", "流入ゲージ", "Influx Gauge"},
                {"outflow", "流出ゲージ", "Outflow Gauge"}, {"switch_outflow", "流出に切替", "Switch to outflow"}, {"switch_influx", "流入に切替", "Switch to influx"},
                {"population", "人口 %s / %s  予約 +%s / -%s", "Population %s / %s  Reserved +%s / -%s"},
                {"center", "中心: %s", "Center: %s"}, {"range", "半径", "Range"}, {"target", "目標人口", "Target"},
                {"name", "地区名", "District name"}, {"center_label", "中心座標", "Center"},
                {"membership", "所属地区: %s", "District: %s"},
                {"population_label", "人口", "Population"}, {"population_summary", "人口 %s / %s", "Population %s / %s"},
                {"reserved", "予約 +%s / -%s", "Reserved +%s / -%s"}, {"next_action", "次の人口操作: %s秒", "Next action: %ss"},
                {"apply", "適用", "Apply"}, {"copy", "地区IDをコピー", "Copy District ID"},
                {"links", "接続ゲージ: %s", "Linked Gauges: %s"}, {"unlink", "切断", "Unlink"},
                {"loaded", "稼働中", "Loaded"}, {"unloaded", "未ロード", "Unloaded"},
                {"cooldown", "次の人口操作まで: %s秒", "Population cooldown: %ss"},
                {"source_help", "接続地区からの要求に応じ、直接接続した地区へ1人ずつ市民を生成します。", "Supplies one Citizen at a time to directly linked Districts when requested."},
                {"overlap", "境界を分割: %s地区", "Shared boundaries: %s District(s)"}, {"overlap_strong", "同じ中心の地区は固定順で区分", "Coincident centers use a stable District order"},
                {"range_invalid", "半径は1～256で入力してください", "Enter a radius from 1 to 256"},
                {"target_invalid", "目標人口は0以上、半径以下で入力してください", "Target population must be between 0 and the radius"},
                {"force", "人口移動を拒否: %s", "Refuse transfers: %s"}, {"independent", "地区から独立: %s", "Independent: %s"},
                {"on", "ON", "ON"}, {"off", "OFF", "OFF"},
                {"connect", "新しい接続を追加", "Add new connection"},
                {"relocate", "このゲージを移動", "Relocate this gauge"},
                {"save_close", "保存して閉じる", "Save and close"}, {"panel", "パネル %s", "Panel %s"},
                {"unlink_help", "右クリックで接続を解除", "Right-click to unlink"}
        };
        for (var entry : entries) add.accept("screen.createapparel.gauge." + entry[0], entry[japanese ? 1 : 2]);
        String[][] messages = {
                {"center_used", "この地点は既に地区の中心として記録・使用されています。", "This location is already recorded or used as a District center."},
                {"center", "地区の中心: %s", "District center: %s"}, {"dimension", "記録した中心とは異なるディメンションです。中心を再設定してください。", "Center is in another dimension. Record a new center."},
                {"selected", "同じ壁面の別のゲージを使用して配線。スニーク使用で中止。", "Use another Gauge on the same wall to connect. Sneak-use to cancel."},
                {"move_selected", "同じ壁面の空き位置を使用してゲージを移動。スニーク使用で中止。", "Use an empty position on the same wall to relocate. Sneak-use to cancel."},
                {"moved", "ゲージを移動しました。", "Gauge relocated."},
                {"linked", "ゲージを接続しました。", "Gauges linked."}, {"invalid", "操作できません。同じ向き・同じ壁面・16ブロック未満・空き位置と権限を確認してください。", "Cannot complete action. Check facing, wall surface, distance under 16 blocks, free slot and permissions."}
        };
        for (var entry : messages) add.accept("message.createapparel.gauge." + entry[0], entry[japanese ? 1 : 2]);
    }
}
