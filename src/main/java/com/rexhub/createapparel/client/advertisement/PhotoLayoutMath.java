package com.rexhub.createapparel.client.advertisement;

import com.rexhub.createapparel.photo.PhotoLayout;

public final class PhotoLayoutMath {
    public record Placement(double scale, double x, double y) { }
    private PhotoLayoutMath() { }
    public static Placement place(double width, double height, double minX, double minY, double maxX, double maxY, PhotoLayout layout) {
        double padding = layout.aspect() == PhotoLayout.Aspect.FIT ? Math.min(width, height) * 0.05 : 0;
        double availableWidth = width - padding * 2, availableHeight = height - padding * 2;
        double subjectWidth = Math.max(0.001, maxX - minX), subjectHeight = Math.max(0.001, maxY - minY);
        double scale = layout.aspect() == PhotoLayout.Aspect.FIT ? Math.min(availableWidth / subjectWidth, availableHeight / subjectHeight)
                : Math.max(availableWidth / subjectWidth, availableHeight / subjectHeight);
        double horizontal = layout.horizontal().ordinal() * 0.5, vertical = layout.vertical().ordinal() * 0.5;
        return new Placement(scale, padding + (availableWidth - subjectWidth * scale) * horizontal - minX * scale,
                padding + (availableHeight - subjectHeight * scale) * vertical - minY * scale);
    }
}
