package com.realmmc.controller.core.modules;

public interface CoreModule {
    String getName();

    String getVersion();

    String getDescription();

    void enable() throws Exception;

    void disable() throws Exception;

    boolean isEnabled();

    default String[] getDependencies() {
        return new String[0];
    }

    default int getPriority() {
        return 0;
    }
}