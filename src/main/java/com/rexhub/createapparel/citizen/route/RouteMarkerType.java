package com.rexhub.createapparel.citizen.route;

import java.util.Locale;

public enum RouteMarkerType {
    NORMAL, GATEWAY, CROSSING;

    public String id() { return name().toLowerCase(Locale.ROOT); }
}
