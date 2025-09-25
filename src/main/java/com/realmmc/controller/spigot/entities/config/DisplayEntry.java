package com.realmmc.controller.spigot.entities.config;

import java.util.List;

public class DisplayEntry {
    private Type type;
    private String world;
    private Double x;
    private Double y;
    private Double z;
    private Float yaw;
    private Float pitch;
    private String item;
    private String message;
    private List<String> lines;
    private Boolean glow;
    private String billboard;
    private Float scale;
    private String texturesValue;
    private String texturesSignature;
    private String id;

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }
    public Double getX() { return x; }
    public void setX(Double x) { this.x = x; }
    public Double getY() { return y; }
    public void setY(Double y) { this.y = y; }
    public Double getZ() { return z; }
    public void setZ(Double z) { this.z = z; }
    public Float getYaw() { return yaw; }
    public void setYaw(Float yaw) { this.yaw = yaw; }
    public Float getPitch() { return pitch; }
    public void setPitch(Float pitch) { this.pitch = pitch; }
    public String getItem() { return item; }
    public void setItem(String item) { this.item = item; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<String> getLines() { return lines; }
    public void setLines(List<String> lines) { this.lines = lines; }
    public Boolean getGlow() { return glow; }
    public void setGlow(Boolean glow) { this.glow = glow; }
    public String getBillboard() { return billboard; }
    public void setBillboard(String billboard) { this.billboard = billboard; }
    public Float getScale() { return scale; }
    public void setScale(Float scale) { this.scale = scale; }
    public String getTexturesValue() { return texturesValue; }
    public void setTexturesValue(String texturesValue) { this.texturesValue = texturesValue; }
    public String getTexturesSignature() { return texturesSignature; }
    public void setTexturesSignature(String texturesSignature) { this.texturesSignature = texturesSignature; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public enum Type {
        DISPLAY_ITEM,
        HOLOGRAM,
        NPC;

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