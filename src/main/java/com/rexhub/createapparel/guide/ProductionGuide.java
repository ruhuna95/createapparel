package com.rexhub.createapparel.guide;

import java.util.Locale;

public enum ProductionGuide {
    SPINNING("Spinning thread", "糸の紡績", new String[][]{
            {"Spinning Machines turn fibers, wool and cocoons into thread.", "繊維・羊毛・繭から糸を作れます。"},
            {"Supply rotational force through a shaft on either side.", "左右のシャフトから回転動力を与えます。"},
            {"Right-click with a fiber to insert it.", "繊維を持って右クリックすると投入できます。"},
            {"The finished thread is ejected above the machine when no transport is connected.", "搬送先がなければ、完成した糸は機械の上に排出されます。"},
            {"Stopping the rotation pauses progress without losing it.", "回転が止まると進捗を保持したまま一時停止します。"}}),
    SPINNING_AUTOMATION("Automating spinning", "紡績の自動化", new String[][]{
            {"Chutes and Funnels can supply fibers automatically.", "シュートやファンネルで繊維を自動搬入できます。"},
            {"A connected Depot can receive the finished thread.", "隣接するデポへ完成した糸を渡せます。"},
            {"If transport cannot accept an output, the remainder drops above the machine.", "搬送先が受け取れない完成品は、残りが機械の上に排出されます。"}}),
    WEAVING("Weaving fabric", "生地の製織", new String[][]{
            {"Looms weave four threads of the same material into one undyed fabric.", "織機は同じ素材の糸4個から未染色の生地1枚を織ります。"},
            {"Supply rotational force through either side shaft.", "左右のシャフトから回転動力を与えます。"},
            {"Right-click with thread to insert it; fewer than four cannot start a batch.", "糸を持って右クリックして投入します。4個未満では製織を始めません。"},
            {"The finished fabric is ejected above the Loom.", "完成した生地は織機の上に排出されます。"}}),
    WEAVING_AUTOMATION("Automating weaving", "製織の自動化", new String[][]{
            {"Chutes and Funnels keep the Loom supplied with thread.", "シュートやファンネルで織機に糸を補給できます。"},
            {"Each batch consumes four matching threads and produces one fabric.", "1回につき同じ素材の糸4個を消費し、生地1枚を作ります。"},
            {"Connected logistics receive the fabric; unaccepted output drops above the Loom.", "完成した生地は搬送先へ渡し、受け取れない分は織機の上に排出します。"}}),
    PATTERN("Preparing a Sewing Pattern", "型紙の設定", new String[][]{
            {"Use a Sewing Pattern in hand to open its editor.", "型紙を手に持って右クリックすると設定画面を開きます。"},
            {"Choose a garment, design and additional colors; closing the editor saves the changes.", "衣服・デザイン・追加色を選び、画面を閉じると保存されます。"},
            {"Apply the configured Pattern to the Sewing Machine's filter panel.", "設定済みの型紙をミシンのフィルター欄に設定します。"},
            {"The Pattern is not consumed. The fabric, rather than the preview, sets the primary color.", "型紙は消費しません。生地色はプレビューではなく、投入する生地で決まります。"}}),
    SEWING("Sewing apparel", "衣服の縫製", new String[][]{
            {"A Sewing Machine combines fabric and thread using its Pattern.", "ミシンは型紙に合わせて生地と糸から衣服を縫います。"},
            {"Connect rotational force to the single shaft at the back.", "背面のシャフトに回転動力を接続します。"},
            {"This T-shirt needs three dyed cotton fabrics and one cotton thread.", "このTシャツには染色済みの綿生地3枚と綿糸1個が必要です。"},
            {"The finished garment inherits the fabric's material and primary color.", "完成した衣服は生地の素材と生地色を引き継ぎます。"}}),
    SEWING_AUTOMATION("Automating sewing", "縫製の自動化", new String[][]{
            {"Set the Pattern once, then supply fabric and thread through Chutes or Funnels.", "型紙を設定し、シュートやファンネルで生地と糸を補給します。"},
            {"Only the required materials are consumed; the Pattern remains in the filter.", "必要数の素材だけを消費します。型紙はフィルターに残ります。"},
            {"A connected Depot receives the garment. Unaccepted output drops above the machine.", "隣接するデポへ衣服を渡せます。受け取れない完成品は機械の上に排出します。"}}),
    REARING("Rearing silkworms", "カイコの収容と養蚕", new String[][]{
            {"Right-click a larva with an empty Tray to capture it.", "未収容の養蚕台で幼虫を右クリックすると収容できます。"},
            {"A placed Tray can also capture a larva above it, or one leashed within seven blocks.", "設置済みの台は、直上の幼虫か7ブロック以内でリードにつないだ幼虫も収容できます。"},
            {"Supply leaves and rotational force to begin rearing.", "葉と回転動力を与えると養蚕を始めます。"},
            {"A cocoon is produced while the larva remains in the Tray.", "幼虫を台に残したまま繭を生産します。"},
            {"Sneak-right-click with an empty hand to release it after the cycle and pending output finish.", "養蚕と出力待ちが終わると、素手でスニークしながら右クリックして幼虫を取り出せます。"}}),
    REARING_AUTOMATION("Automating silkworm rearing", "養蚕の自動化", new String[][]{
            {"Supply food through a Chute; only food can be inserted automatically.", "シュートで餌を補給できます。自動搬入は餌だけを受け付けます。"},
            {"Hoppers and Funnels extract only finished cocoons, leaving the larva inside.", "ホッパーやファンネルは完成した繭だけを搬出し、幼虫は台に残ります。"},
            {"A redstone signal pauses production without discarding progress.", "レッドストーン信号で進捗を保持したまま一時停止します。"},
            {"Extracted cocoons can be sent to a Spinning Machine for silk thread.", "搬出した繭は紡績機で絹糸に加工できます。"}});

    private final String englishTitle;
    private final String japaneseTitle;
    private final String[][] text;

    ProductionGuide(String englishTitle, String japaneseTitle, String[][] text) {
        this.englishTitle = englishTitle;
        this.japaneseTitle = japaneseTitle;
        this.text = text;
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String title(boolean japanese) { return japanese ? japaneseTitle : englishTitle; }
    public int textCount() { return text.length; }
    public String text(int index, boolean japanese) { return text[index][japanese ? 1 : 0]; }
}
