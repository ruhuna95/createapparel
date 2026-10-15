package com.rexhub.createapparel.data.client;

import java.util.function.BiConsumer;

final class RouteLanguage {
    private RouteLanguage() { }
    static void add(BiConsumer<String, String> add, boolean japanese) {
        add.accept("behavior.createapparel.walk", japanese ? "経路歩行" : "Walk");
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
                {"enabled", "Enabled", "有効"}, {"gateway_disabled", "Disabled: Citizen Gauge unavailable", "無効：Citizen Gauge未実装"},
                {"threshold", "Signal threshold", "信号しきい値"}, {"node_state", "Node:", "ノード状態："},
                {"state.on", "ON", "ON"}, {"state.off", "OFF", "OFF"},
                {"control_hint", "Use the smartphone to right-click a crossing marker, then a redstone source block.", "スマートフォンで横断マーカー、レッドストーン信号を出すブロックの順に右クリックして接続します。"},
                {"inverted.false", "Invert: Off", "条件反転：オフ"}, {"inverted.true", "Invert: On", "条件反転：オン"},
                {"no_type_settings", "Normal markers have no type-specific settings.", "通常マーカーには固有の設定がありません。"},
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
        add.accept("tooltip.createapparel.smart_phone.routes", japanese ? "メインハンドで経路を編集。地面上面にスニーク右クリックで設置、マーカー2点を右クリックで接続、スニークで設定。オフハンドは表示専用。"
                : "Main hand: sneak-use ground to place, right-click markers to link, sneak-use to configure. Offhand: view only.");
        for (var type : new String[][]{{"citizen_route_marker", "Route Marker", "経路マーカー"}, {"gateway_route_marker", "Gateway Route Marker", "ゲートウェイ経路マーカー"},
                {"crossing_route_marker", "Crossing Route Marker", "横断経路マーカー"}}) add.accept("block.createapparel." + type[0], type[japanese ? 2 : 1]);
    }
}
