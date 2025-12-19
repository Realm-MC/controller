package com.palacesky.controller.core;

import lombok.Getter;

import java.util.TimeZone;
import java.util.logging.Logger;

public abstract class ControllerCore {
    @Getter
    protected static ControllerCore instance;

    @Getter
    protected final Logger logger;

    protected ControllerCore(Logger logger) {
        this.logger = logger;
        instance = this;

        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
        logger.info("Fuso horário definido globalmente para: America/Sao_Paulo");
    }

    public abstract void initialize();

    public abstract void shutdown();

    protected void initializeSharedServices() {
        logger.info("Inicializando serviços compartilhados do Controller Core...");
    }

    protected void shutdownSharedServices() {
        logger.info("Finalizando serviços compartilhados do Controller Core...");
    }
}