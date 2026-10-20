package com.rexhub.createapparel.client.garment;

public final class CitizenRenderLod {
    private CitizenRenderLod() { }

    public static int select(int previous, double distance, double near, double middle, double far, double margin) {
        middle = Math.max(near, middle);
        far = Math.max(middle, far);
        margin = Math.min(margin, near * .5);
        int level = previous;
        if (level < 0) {
            level = 0;
            while (level < 3 && distance >= boundary(level, near, middle, far)) level++;
        } else {
            while (level < 3 && distance >= boundary(level, near, middle, far) + margin) level++;
            while (level > 0 && distance < boundary(level - 1, near, middle, far) - margin) level--;
        }
        return level;
    }
    private static double boundary(int level, double near, double middle, double far) {
        return level == 0 ? near : level == 1 ? middle : far;
    }
}
