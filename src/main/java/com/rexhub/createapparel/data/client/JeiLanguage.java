package com.rexhub.createapparel.data.client;

import java.util.function.BiConsumer;

final class JeiLanguage {
    private JeiLanguage() { }

    static void add(BiConsumer<String, String> add, boolean japanese) {
        add.accept("jei.createapparel.category.spinning", japanese ? "紡績" : "Spinning");
        add.accept("jei.createapparel.category.weaving", japanese ? "製織" : "Weaving");
        add.accept("jei.createapparel.category.sewing", japanese ? "縫製" : "Sewing");
        add.accept("jei.createapparel.category.silkworm_rearing", japanese ? "養蚕" : "Silkworm Rearing");
        add.accept("jei.createapparel.fabric", japanese ? "生地" : "Fabric");
        add.accept("jei.createapparel.thread", japanese ? "糸" : "Thread");
        add.accept("jei.createapparel.chance", japanese ? "出力確率: %s%%" : "Output chance: %s%%");
        add.accept("jei.createapparel.sewing.pattern", japanese
                ? "このレシピを指定した型紙が必要" : "Requires a pattern configured for this recipe.");
    }
}
