package com.rexhub.createapparel.entity;

import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;

public enum MannequinVariant {
    FULL_WHITE_CLASSIC(MannequinForm.FULL, MannequinColor.WHITE, false),
    FULL_BLACK_CLASSIC(MannequinForm.FULL, MannequinColor.BLACK, false),
    FULL_WHITE_SLIM(MannequinForm.FULL, MannequinColor.WHITE, true),
    FULL_BLACK_SLIM(MannequinForm.FULL, MannequinColor.BLACK, true),
    UPPER_WHITE_CLASSIC(MannequinForm.UPPER, MannequinColor.WHITE, false),
    UPPER_BLACK_CLASSIC(MannequinForm.UPPER, MannequinColor.BLACK, false),
    UPPER_WHITE_SLIM(MannequinForm.UPPER, MannequinColor.WHITE, true),
    UPPER_BLACK_SLIM(MannequinForm.UPPER, MannequinColor.BLACK, true),
    LOWER_WHITE(MannequinForm.LOWER, MannequinColor.WHITE, false),
    LOWER_BLACK(MannequinForm.LOWER, MannequinColor.BLACK, false),
    HEAD_WHITE(MannequinForm.HEAD, MannequinColor.WHITE, false),
    HEAD_BLACK(MannequinForm.HEAD, MannequinColor.BLACK, false);

    private final MannequinForm form;
    private final MannequinColor color;
    private final ArmProfile arms;
    private final ChestProfile chest;
    private final String id;

    MannequinVariant(MannequinForm form, MannequinColor color, boolean slim) {
        this.form = form;
        this.color = color;
        arms = slim ? ArmProfile.SLIM : ArmProfile.CLASSIC;
        chest = slim ? ChestProfile.BUST : ChestProfile.FLAT;
        id = form.name().toLowerCase(java.util.Locale.ROOT) + "_mannequin_" + color.id()
                + (form.hasTorso() ? (slim ? "_slim" : "_classic") : "");
    }

    public String id() { return id; }
    public MannequinForm form() { return form; }
    public MannequinColor color() { return color; }
    public ArmProfile armProfile() { return arms; }
    public ChestProfile chestProfile() { return chest; }
}
