package com.realmmc.controller.core.modules;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca um módulo para ser registado automaticamente pelo ModuleManager.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AutoRegister {

    /**
     * Define em que plataformas (Spigot, Proxy) o módulo deve ser carregado.
     * O padrão é carregar em todas.
     */
    Platform[] platforms() default {Platform.ALL};

    enum Platform {
        PROXY,
        SPIGOT,
        ALL // Usado para módulos compartilhados
    }
}