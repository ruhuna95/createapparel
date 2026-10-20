package com.rexhub.createapparel.data.client;

import java.util.function.BiConsumer;

final class RouteLanguage {
    private RouteLanguage() { }
    static void add(BiConsumer<String, String> add, boolean japanese) {
        add.accept("behavior.createapparel.walk", japanese ? "経路歩行" : "Walk");
        add.accept("message.createapparel.route.support_removed", japanese ? "支持ブロックの撤去によりマーカー（%s）と接続%s本が削除されました。" : "Support removed: marker at %s and %s connections were removed.");
        add.accept("message.createapparel.route.marker_removed", japanese ? "マーカー（%s）が消失し、接続%s本が削除されました。" : "Marker at %s disappeared; %s connections were removed.");
        add.accept("message.createapparel.route.disconnected", japanese ? "未接続の経路マーカー" : "Disconnected route marker");
        add.accept("message.createapparel.route.statistics", japanese ? "Navigation：%s／待機%s、Graph探索：%s／Node %s／Edge %s／待機%s、処理時間：%s ms" : "Navigation: %s / queued %s; graph searches: %s / nodes %s / edges %s / queued %s; time: %s ms");
        add.accept("container.createapparel.route_marker", japanese ? "経路マーカー" : "Route Marker");
        for (var entry : new String[][]{
                {"settings", "Settings", "設定"}, {"type", "Type Settings", "タイプ設定"},
                {"radius", "XZ radius", "水平半径"}, {"tolerance", "Y tolerance", "高さ許容"}, {"blocks", "blocks", "ブロック"},
                {"allowed_values", "Allowed: %s", "許容値：%s"},
                {"marker_type", "Marker type", "マーカータイプ"},
                {"type.normal", "Normal", "通常"}, {"type.gateway", "Gateway", "ゲートウェイ"}, {"type.crossing", "Crossing", "横断"},
                {"delete_marker", "Delete Marker", "マーカーを削除"},
                {"open", "Open", "通行可"}, {"closed", "Closed", "通行不可"}, {"unloaded", " (unloaded)", "（未ロード）"},
                {"district", "District UUID", "地区UUID"},
                {"affiliation", "District", "所属地区"},
                {"unassigned", "Unassigned", "未所属"},
                {"destinations", "Destination districts", "移動先地区"},
                {"no_destinations", "No linked districts. Connect District Gauges to add destinations.", "移動先地区はありません。地区ゲージ同士を接続してください。"},
                {"available", "Available", "利用可能"}, {"unavailable", "Unavailable", "利用不可"},
                {"destination_page", "%s–%s / %s (scroll)", "%s～%s / %s（スクロール）"},
                {"enabled", "Enabled", "有効"}, {"gateway_disabled", "Disabled: no valid District Gauge", "無効：有効な地区ゲージなし"},
                {"threshold", "Signal threshold", "信号しきい値"}, {"node_state", "Node:", "ノード状態："},
                {"state.on", "ON", "ON"}, {"state.off", "OFF", "OFF"},
                {"control_hint", "Use the smartphone to right-click a crossing marker, then a redstone source block.", "スマートフォンで横断マーカー、レッドストーン信号を出すブロックの順に右クリックして接続する。"},
                {"inverted.false", "Invert: Off", "条件反転：オフ"}, {"inverted.true", "Invert: On", "条件反転：オン"},
                {"no_type_settings", "Normal markers have no type-specific settings.", "通常マーカーには固有の設定はない。"},
                {"update_timeout", "Settings update timed out. Check the marker before trying again.", "設定更新がタイムアウトしました。マーカーの状態を確認してください。"}
        }) add.accept("screen.createapparel.route." + entry[0], entry[japanese ? 2 : 1]);
        for (var entry : new String[][]{
                {"selected", "Start selected. Right-click another marker to link; click again to cancel.", "始点を選択しました。別マーカーを右クリックで接続、同じマーカーで解除。"},
                {"cancelled", "Link selection cancelled.", "リンク選択を解除しました。"},
                {"linked", "Bidirectional connection created.", "双方向の接続を作成しました。"},
                {"edge_deleted", "Connection deleted.", "接続を削除しました。"},
                {"control_linked", "Redstone control block connected.", "レッドストーン制御ブロックを接続しました。"},
                {"control_unlinked", "Control block disconnected.", "制御ブロックの接続を解除しました。"},
                {"control_requires_crossing", "Select a crossing marker to connect a redstone control block.", "制御ブロックの接続には横断マーカーを選択してください。"},
                {"control_invalid", "Connect a reachable, editable redstone source block within 64 blocks of the marker.", "マーカーから64ブロック以内の、照準可能で編集が許可されたレッドストーン出力ブロックへ接続してください。"},
                {"duplicate", "This connection already exists.", "この接続は既に存在します。"},
                {"denied", "Cannot edit this marker.", "このマーカーは編集できません。"},
                {"placement_failed", "Place on the top of a solid, dry ground block with empty space above.", "上側に空間がある、液体を含まない地面ブロックの上面に設置してください。"},
                {"stale", "The marker changed. Settings have been refreshed; try again.", "マーカーが更新されました。最新の設定を確認して再操作してください。"},
                {"invalid", "Invalid or unchanged settings, or unavailable connection.", "設定が不正・同値、または接続先が利用できません。"}
        }) add.accept("message.createapparel.route." + entry[0], entry[japanese ? 2 : 1]);
        for (var type : new String[][]{{"citizen_route_marker", "Route Marker", "経路マーカー"}, {"gateway_route_marker", "Gateway Route Marker", "ゲートウェイ経路マーカー"},
                {"crossing_route_marker", "Crossing Route Marker", "横断経路マーカー"}}) add.accept("block.createapparel." + type[0], type[japanese ? 2 : 1]);
    }
}
