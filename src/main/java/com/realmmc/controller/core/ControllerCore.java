package com.realmmc.controller.core;

import lombok.Getter;
import java.util.logging.Logger;

public abstract class ControllerCore {
    @Getter
    protected static ControllerCore instance;
    
    @Getter
    protected final Logger logger;
    
    protected ControllerCore(Logger logger) {
        this.logger = logger;
        instance = this;
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