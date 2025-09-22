package com.realmmc.controller.spigot.display.config;

import lombok.Data;

@Data
public class DisplayEntry {
    private Integer id;
    private Type type;
    private String world;
    private Double x;
    private Double y;
    private Double z;
    private Float yaw;
    private Float pitch;
    private String item;
    private String message;
    private java.util.List<String> lines;
    private Boolean glow;
    private String billboard;
    private Float scale;

    public enum Type {
        DISPLAY_ITEM;

        public static Type fromString(String str) {
            if (str == null) return null;
            try {
                return Type.valueOf(str.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
