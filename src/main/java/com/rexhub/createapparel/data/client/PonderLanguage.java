package com.rexhub.createapparel.data.client;

import com.rexhub.createapparel.guide.ProductionGuide;
import java.util.function.BiConsumer;

final class PonderLanguage {
    private PonderLanguage() { }

    static void add(BiConsumer<String, String> add, boolean japanese) {
        add.accept("createapparel.ponder.tag.retail", japanese ? "衣服の店舗" : "Apparel Shops");
        add.accept("createapparel.ponder.tag.retail.description", japanese ? "店員・在庫・展示品のリンク" : "Cashiers, storage and linked displays");
        String retail = "createapparel.ponder." + com.rexhub.createapparel.guide.RetailGuide.ID + ".";
        add.accept(retail + "header", com.rexhub.createapparel.guide.RetailGuide.title(japanese));
        for (int index = 0; index < com.rexhub.createapparel.guide.RetailGuide.textCount(); index++)
            add.accept(retail + "text_" + (index + 1), com.rexhub.createapparel.guide.RetailGuide.text(index, japanese));
        add.accept("createapparel.ponder.tag.production", japanese ? "衣服の生産" : "Apparel Production");
        add.accept("createapparel.ponder.tag.production.description", japanese
                ? "紡績・製織・縫製・養蚕の工程" : "Spinning, weaving, sewing and silkworm rearing");
        for (var guide : ProductionGuide.values()) {
            String key = "createapparel.ponder." + guide.id() + ".";
            add.accept(key + "header", guide.title(japanese));
            for (int index = 0; index < guide.textCount(); index++) {
                add.accept(key + "text_" + (index + 1), guide.text(index, japanese));
            }
        }
    }
}
