package com.rexhub.createapparel.entity;

public enum MannequinColor {
    WHITE("white"), BLACK("black");

    private final String id;
    MannequinColor(String id) { this.id = id; }
    public String id() { return id; }
}
