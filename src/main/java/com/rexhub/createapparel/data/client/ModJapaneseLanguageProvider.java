package com.rexhub.createapparel.data.client;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.material.FabricAttribute;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.data.LanguageProvider;
import net.minecraftforge.registries.RegistryObject;

public final class ModJapaneseLanguageProvider extends LanguageProvider {
    public ModJapaneseLanguageProvider(PackOutput output) {
        super(output, CreateApparel.MOD_ID, "ja_jp");
    }

    @Override
    protected void addTranslations() {
        add("itemGroup.createapparel", "Create: Apparel");
        add("itemGroup.createapparel.garment", "Create Apparel - 衣服");
        add("tooltip.createapparel.garment.slot", "%s用衣服");
        add("tooltip.createapparel.garment.material", "生地: %s");
        add("tooltip.createapparel.garment.primary_color", "生地色: %s");
        add("tooltip.createapparel.garment.design", "デザイン: %s");
        add("tooltip.createapparel.garment.secondary_color", "サブカラー: %s");
        add("tooltip.createapparel.garment.accent_color", "アクセントカラー: %s");
        add("tooltip.createapparel.watch.check_time", "装備中、両手が空なら左Shift長押しで時間を確認できます");
        for (FabricMaterial material : FabricMaterial.values()) {
            add("fabric_material.createapparel." + material.getSerializedName(), materialName(material));
        }
        add("tooltip.createapparel.machine.fiber", "繊維：%s");
        add("tooltip.createapparel.machine.thread", "糸：%s");
        add("tooltip.createapparel.machine.fabric", "生地：%s");
        add("tooltip.createapparel.machine.empty", "なし");
        add("tooltip.createapparel.machine.pattern", "型紙：%s");

        addBlock(ModBlocks.COTTON_CROP, "綿花の作物");
        addBlock(ModBlocks.FLAX_CROP, "亜麻の作物");
        addBlock(ModBlocks.HEMP_CROP, "ヘンプの作物");
        addBlock(ModBlocks.SPINNING_MACHINE, "紡績機");
        addBlock(ModBlocks.LOOM, "織機");
        addBlock(ModBlocks.SEWING_MACHINE, "ミシン");
        addBlock(ModBlocks.WARDROBE, "ワードローブ");
        addEntityType(ModEntities.SILKWORM, "カイコ");

        add(ModItems.COTTON_SEEDS.get(), "綿花の種");
        add(ModItems.COTTON_BOLL.get(), "綿花");
        add(ModItems.COTTON_FIBER.get(), "綿繊維");
        add(ModItems.COTTON_THREAD.get(), "綿糸");
        add(ModItems.FLAX_SEEDS.get(), "亜麻の種");
        add(ModItems.FLAX.get(), "亜麻");
        add(ModItems.FLAX_FIBER.get(), "亜麻繊維");
        add(ModItems.LINEN_THREAD.get(), "リネン糸");
        add(ModItems.HEMP_SEEDS.get(), "ヘンプの種");
        add(ModItems.HEMP.get(), "ヘンプ");
        add(ModItems.HEMP_FIBER.get(), "ヘンプ繊維");
        add(ModItems.HEMP_THREAD.get(), "ヘンプ糸");
        add(ModItems.WOOL_THREAD.get(), "毛糸");
        add(ModItems.SILK_THREAD.get(), "絹糸");
        add(ModItems.COCOON.get(), "繭");
        add(ModItems.SILKWORM_SPAWN_EGG.get(), "カイコのスポーンエッグ");
        add(ModItems.SYNTHETIC_FIBER.get(), "合成繊維");
        add(ModItems.SYNTHETIC_THREAD.get(), "合成糸");
        add(ModItems.SEWING_PATTERN.get(), "型紙");
        add(ModItems.T_SHIRT.get(), "Tシャツ");
        add(ModItems.T_SHIRT_U_NECK.get(), "UネックTシャツ");
        add(ModItems.T_SHIRT_V_NECK.get(), "VネックTシャツ");
        add(ModItems.T_SHIRT_LONGSLEEVES.get(), "長袖Tシャツ");
        add(ModItems.T_SHIRT_LONGSLEEVES_U_NECK.get(), "Uネック長袖Tシャツ");
        add(ModItems.T_SHIRT_LONGSLEEVES_V_NECK.get(), "Vネック長袖Tシャツ");
        add(ModItems.SHIRT_SHORTSLEEVES.get(), "半袖ワイシャツ");
        add(ModItems.SHIRT_LONGSLEEVES.get(), "長袖ワイシャツ");
        add(ModItems.BLOUSE_ROUND.get(), "丸襟ブラウス");
        add(ModItems.PULLOVER_HOODIE.get(), "プルオーバーパーカー");
        add(ModItems.SWEATER.get(), "セーター");
        add(ModItems.V_NECK_SWEATER.get(), "Vネックセーター");
        add(ModItems.DENIM_PANTS.get(), "デニムパンツ");
        add(ModItems.SLACKS.get(), "スラックス");
        add(ModItems.SHORTS.get(), "ショートパンツ");
        add(ModItems.A_LINE_SKIRT.get(), "Aラインスカート");
        add(ModItems.TIGHTS.get(), "タイツ");
        add(ModItems.ANKLE_SOCKS.get(), "アンクルソックス");
        add(ModItems.CREW_SOCKS.get(), "クルーソックス");
        add(ModItems.KNEE_HIGH_SOCKS.get(), "ハイソックス");
        add(ModItems.OVER_KNEE_SOCKS.get(), "ニーソックス");
        add(ModItems.THIGH_HIGH_SOCKS.get(), "サイハイソックス");
        add(ModItems.SUIT_JACKET.get(), "スーツジャケット");
        add(ModItems.BLAZER_JACKET.get(), "ブレザージャケット");
        add(ModItems.DENIM_JACKET.get(), "デニムジャケット");
        add(ModItems.CHESTERFIELD_COAT.get(), "チェスターコート");
        add(ModItems.TRENCH_COAT.get(), "トレンチコート");
        add(ModItems.DUFFLE_COAT.get(), "ダッフルコート");
        add(ModItems.PEA_COAT.get(), "ピーコート");
        add(ModItems.UNDERSHIRT.get(), "肌着");
        add(ModItems.BRA.get(), "ブラ");
        add(ModItems.BRIEFS.get(), "ブリーフ");
        add(ModItems.BOXER_BRIEFS.get(), "ボクサーブリーフ");
        add(ModItems.PANTIES.get(), "ショーツ");
        add(ModItems.SNEAKERS.get(), "スニーカー");
        add(ModItems.CANVAS_SNEAKERS.get(), "キャンバススニーカー");
        add(ModItems.DRESS_SHOES.get(), "革靴");
        add(ModItems.LOAFERS.get(), "ローファー");
        add(ModItems.ANKLE_BOOTS.get(), "アンクルブーツ");
        add(ModItems.WORK_BOOTS.get(), "ワークブーツ");
        add(ModItems.KNEE_HIGH_BOOTS.get(), "ロングブーツ");
        add(ModItems.PUMPS.get(), "パンプス");
        add(ModItems.SANDALS.get(), "サンダル");
        add(ModItems.SLIPPERS.get(), "スリッパ");
        add(ModItems.CAP.get(), "キャップ");
        add(ModItems.BEANIE.get(), "ニットキャップ");
        add(ModItems.BERET.get(), "ベレー帽");
        add(ModItems.HAT.get(), "ハット");
        add(ModItems.CASQUETTE.get(), "キャスケット");
        add(ModItems.SUN_VISOR.get(), "サンバイザー");
        add(ModItems.GLASSES.get(), "メガネ");
        add(ModItems.SUNGLASSES.get(), "サングラス");
        add(ModItems.MASK.get(), "マスク");
        add(ModItems.BACKPACK.get(), "バックパック");
        add(ModItems.DAYPACK.get(), "デイパック");
        add(ModItems.CROSSBODY_BAG.get(), "ボディバッグ");
        add(ModItems.NECKLACE.get(), "ネックレス");
        add(ModItems.NECKTIE.get(), "ネクタイ");
        add(ModItems.WATCH.get(), "腕時計");
        add(ModItems.BRACELET.get(), "ブレスレット");
        add(ModItems.WRISTBAND.get(), "リストバンド");
        add(ModItems.GLOVES.get(), "手袋");
        add(ModItems.GOLD_BRACELET_BASE.get(), "金のブレスレット素体");
        add(ModItems.SILVER_BRACELET_BASE.get(), "銀のブレスレット素体");
        add(ModItems.GOLD_NECKLACE_BASE.get(), "金のネックレス素体");
        add(ModItems.SILVER_NECKLACE_BASE.get(), "銀のネックレス素体");
        add("tooltip.createapparel.accessory.metal", "金属: %s");
        add("tooltip.createapparel.accessory.gem", "ジェム: %s");
        add("metal_color.createapparel.gold", "金");
        add("metal_color.createapparel.silver", "銀");
        add("gem.createapparel.diamond", "ダイヤモンド");
        add("gem.createapparel.emerald", "エメラルド");
        add("gem.createapparel.amethyst", "アメジスト");
        add("gem.createapparel.ruby", "ルビー");
        add("gem.createapparel.sapphire", "サファイア");
        add("gem.createapparel.pearl", "パール");
        add("style_category.createapparel.carry", "掛け方");
        add("style.createapparel.carry.back", "後ろ掛け");
        add("style.createapparel.carry.front", "前掛け");
        add(ModItems.ZIPUP_HOODIE.get(), "ジップアップパーカー");
        add(ModItems.BLOUSE_FRILL.get(), "フリルブラウス");
        add(ModItems.BLOUSE_BOW_TIE.get(), "ボウタイブラウス");
        add(ModItems.BLOUSE_ROUND_THREE_QUARTER_SLEEVE.get(), "丸襟七分丈ブラウス");
        add(ModItems.BLOUSE_FRILL_THREE_QUARTER_SLEEVE.get(), "フリル七分丈ブラウス");
        add(ModItems.BLOUSE_BOW_TIE_THREE_QUARTER_SLEEVE.get(), "ボウタイ七分丈ブラウス");

        add("tooltip.createapparel.sewing_pattern.unset", "右クリックで設定");
        add("tooltip.createapparel.sewing_pattern.recipe", "レシピ: %s");
        add("tooltip.createapparel.sewing_pattern.unknown_garment", "不明な衣服");
        add("tooltip.createapparel.sewing_pattern.design", "デザイン: %s");
        add("tooltip.createapparel.sewing_pattern.secondary_color", "サブカラー: %s");
        add("tooltip.createapparel.sewing_pattern.accent_color", "アクセント: %s");
        add("screen.createapparel.sewing_pattern", "型紙の設定");
        add("screen.createapparel.sewing_pattern.category", "カテゴリ: %s");
        add("screen.createapparel.sewing_pattern.no_categories", "衣服カテゴリがありません");
        add("screen.createapparel.sewing_pattern.no_recipes", "縫製レシピがありません");
        add("screen.createapparel.sewing_pattern.no_designs", "デザインがありません");
        add("screen.createapparel.sewing_pattern.recipe", "衣服: %s");
        add("screen.createapparel.sewing_pattern.design", "デザイン: %s");
        add("screen.createapparel.sewing_pattern.fabric_color", "プレビュー生地色：%s");
        add("screen.createapparel.sewing_pattern.secondary_color", "サブカラー: %s");
        add("screen.createapparel.sewing_pattern.accent_color", "アクセント: %s");
        add("screen.createapparel.sewing_pattern.preview", "完成プレビュー");
        add("screen.createapparel.sewing_pattern.fabric_required", "必要な生地：%s");
        add("screen.createapparel.sewing_pattern.thread_required", "必要な糸：%s");
        add("container.createapparel.wardrobe", "ワードローブ");
        add("screen.createapparel.wardrobe.apparel_slots", "アパレルスロット");
        add("screen.createapparel.wardrobe.selected_garment", "選択中の衣服: %s");
        add("screen.createapparel.wardrobe.empty", "なし");
        add("screen.createapparel.wardrobe.no_styles", "変更できるスタイルはありません");
        add("screen.createapparel.wardrobe.player_preview", "プレイヤープレビュー");
        add("screen.createapparel.wardrobe.chest_profile", "胸部: %s");
        add("screen.createapparel.wardrobe.skin_tone", "肌色: %s");
        add("screen.createapparel.wardrobe.style", "%s: %s");
        add("chest_profile.createapparel.flat", "フラット");
        add("chest_profile.createapparel.bust", "バスト");
        add("skin_tone.createapparel.snow", "白肌");
        add("skin_tone.createapparel.very_fair", "とても色白");
        add("skin_tone.createapparel.fair", "色白");
        add("skin_tone.createapparel.natural", "ナチュラル");
        add("skin_tone.createapparel.healthy", "健康肌");
        add("skin_tone.createapparel.tanned", "小麦肌");
        add("skin_tone.createapparel.light_brown", "褐色肌");
        add("skin_tone.createapparel.brown", "ブラウン");
        add("skin_tone.createapparel.dark", "濃い肌");
        add("style_category.createapparel.tuck", "裾");
        add("style_category.createapparel.sleeves", "袖");
        add("style_category.createapparel.open", "前開き");
        add("style_category.createapparel.hood", "フード");
        add("style_category.createapparel.wear", "着用方法");
        add("style.createapparel.wear.front", "前被り");
        add("style.createapparel.wear.back", "後ろ被り");
        add("style.createapparel.wear.up", "上げる");
        add("style.createapparel.wear.down", "下げる");
        add("style.createapparel.wear.left", "左腕");
        add("style.createapparel.wear.right", "右腕");
        add("style.createapparel.tuck.untucked", "裾出し");
        add("style.createapparel.tuck.tucked", "タックイン");
        add("style.createapparel.sleeves.normal", "通常");
        add("style.createapparel.sleeves.rolled", "腕まくり");
        add("style.createapparel.open.closed", "閉じる");
        add("style.createapparel.open.opened", "第1ボタンを開ける");
        add("style.createapparel.zipup_hoodie.open.opened", "開く");
        add("style.createapparel.open.top_opened", "上部を開く");
        add("style.createapparel.open.open", "開く");
        add("style.createapparel.hood.down", "下ろす");
        add("style.createapparel.hood.up", "かぶる");
        add("createapparel.sewing_machine.pattern", "型紙");
        add("garment_slot.createapparel.inner_top", "インナートップ");
        add("garment_slot.createapparel.top", "トップス");
        add("garment_slot.createapparel.outer", "アウター");
        add("garment_slot.createapparel.inner_bottom", "インナーボトム");
        add("garment_slot.createapparel.bottom", "ボトムス");
        add("garment_slot.createapparel.legwear", "レッグウェア");
        add("garment_slot.createapparel.feet", "フットウェア");
        add("garment_slot.createapparel.head", "ヘッドウェア");
        add("garment_slot.createapparel.face", "フェイスウェア");
        add("garment_slot.createapparel.back", "バック");
        add("garment_slot.createapparel.hands", "ハンド");
        add("garment_slot.createapparel.neck", "ネック");
        add("design.createapparel.plain", "無地");
        add("design.createapparel.stripe", "ストライプ");
        add("design.createapparel.logo", "ロゴ");
        add("design.createapparel.stripe_logo", "ストライプ＋ロゴ");

        for (var entry : ModItems.fabricItems().entrySet()) {
            add(entry.getKey(), fabricName(entry.getValue()));
        }
    }

    private void add(RegistryObject<? extends Item> item, String translation) {
        add(item.get().getDescriptionId(), translation);
    }

    private static String fabricName(FabricAttribute attribute) {
        String fabric = materialName(attribute.material()) + "生地";
        return attribute.color()
                .map(color -> colorName(color) + "の" + fabric)
                .orElse(fabric);
    }

    private static String materialName(FabricMaterial material) {
        return switch (material) {
            case COTTON -> "コットン";
            case LINEN -> "リネン";
            case HEMP -> "ヘンプ";
            case WOOL -> "ウール";
            case SILK -> "シルク";
            case LEATHER -> "レザー";
            case SYNTHETIC -> "合成";
        };
    }

    private static String colorName(FabricColor color) {
        return switch (color) {
            case WHITE -> "白色";
            case ORANGE -> "橙色";
            case MAGENTA -> "赤紫色";
            case LIGHT_BLUE -> "空色";
            case YELLOW -> "黄色";
            case LIME -> "黄緑色";
            case PINK -> "桃色";
            case GRAY -> "灰色";
            case LIGHT_GRAY -> "薄灰色";
            case CYAN -> "青緑色";
            case PURPLE -> "紫色";
            case BLUE -> "青色";
            case BROWN -> "茶色";
            case GREEN -> "緑色";
            case RED -> "赤色";
            case BLACK -> "黒色";
        };
    }
}
