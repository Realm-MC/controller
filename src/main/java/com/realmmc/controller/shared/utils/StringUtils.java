package com.realmmc.controller.shared.utils;

public class StringUtils {
    public static String generateId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}