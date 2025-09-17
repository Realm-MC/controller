package com.realmmc.controller.spigot.display.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisplayEntry {
    private int id;
    private Type type;
    private String item;
    private Action action;
    private String message;

    public enum Type {
        DISPLAY_ITEM;
        public static Type fromString(String s) {
            try {
                return s == null ? null : Type.valueOf(s.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public enum Action {
        SEND_MESSAGE;
        public static Action fromString(String s) {
            try {
                return s == null ? null : Action.valueOf(s.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}
