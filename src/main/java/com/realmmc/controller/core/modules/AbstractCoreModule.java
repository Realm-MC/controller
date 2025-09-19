package com.realmmc.controller.core.modules;

import lombok.Getter;
import java.util.logging.Logger;

public abstract class AbstractCoreModule implements CoreModule {
    @Getter
    private boolean enabled = false;
    
    protected final Logger logger;
    
    protected AbstractCoreModule(Logger logger) {
        this.logger = logger;
    }
    
    @Override
    public final void enable() throws Exception {
        if (enabled) {
            logger.warning("Módulo " + getName() + " já está habilitado!");
            return;
        }
        
        onEnable();
        enabled = true;
    }
    
    @Override
    public final void disable() throws Exception {
        if (!enabled) {
            logger.warning("Módulo " + getName() + " já está desabilitado!");
            return;
        }
        
        onDisable();
        enabled = false;
    }
    
    protected abstract void onEnable() throws Exception;
    protected abstract void onDisable() throws Exception;
}