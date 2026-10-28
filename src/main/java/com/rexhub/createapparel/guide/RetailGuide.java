package com.rexhub.createapparel.guide;

public final class RetailGuide {
    public static final String ID = "retail";
    private static final String[][] TEXT = {
            {"A Cash Register sells garments from linked storage; it holds no stock itself.", "レジはリンクした在庫から衣服を販売します。レジ自体に在庫は入りません。"},
            {"Place a Create Seat directly in front of the register, at the same height or one block lower.", "Createシートはレジの正面の同じ高さ、またはその1ブロック下に置きます。"},
            {"Any living entity can be the cashier. The shop opens when the seat is occupied and storage is connected.", "生物なら誰でも店員になれます。店員が着席し、在庫が接続されると開店します。"},
            {"Use a Smart Phone on the register to start linking.", "スマートフォンでレジを右クリックして設定を開始します。"},
            {"Sneak-use storage with the same phone to link it. Up to eight storages within 64 blocks are supported; repeat to unlink.", "同じスマートフォンで在庫をスニーク右クリックして接続します。64ブロック以内に最大8か所。再操作で解除します。"},
            {"Sneak-use mannequins and advertisements to set this shop as their purchase destination.", "マネキンや広告もスニーク右クリックして、この店舗を購入先に設定します。"}
    };

    private RetailGuide() { }
    public static String title(boolean japanese) { return japanese ? "店舗の設置とリンク" : "Setting up an apparel shop"; }
    public static int textCount() { return TEXT.length; }
    public static String text(int index, boolean japanese) { return TEXT[index][japanese ? 1 : 0]; }
}
