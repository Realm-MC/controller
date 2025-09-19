package com.realmmc.controller.core.services;

import lombok.Getter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public class ServiceRegistry {
    @Getter
    private static ServiceRegistry instance;
    
    private final Map<Class<?>, Object> services = new HashMap<>();
    private final Logger logger;
    
    public ServiceRegistry(Logger logger) {
        this.logger = logger;
        instance = this;
    }
    
    public <T> void registerService(Class<T> serviceClass, T implementation) {
        if (services.containsKey(serviceClass)) {
            logger.warning("Serviço já registrado: " + serviceClass.getSimpleName());
            return;
        }
        
        services.put(serviceClass, implementation);
        logger.info("Serviço registrado: " + serviceClass.getSimpleName());
    }
    
    public <T> void unregisterService(Class<T> serviceClass) {
        if (services.remove(serviceClass) != null) {
            logger.info("Serviço removido: " + serviceClass.getSimpleName());
        }
    }
    
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getService(Class<T> serviceClass) {
        return Optional.ofNullable((T) services.get(serviceClass));
    }
    
    @SuppressWarnings("unchecked")
    public <T> T requireService(Class<T> serviceClass) {
        T service = (T) services.get(serviceClass);
        if (service == null) {
            throw new IllegalStateException("Serviço não encontrado: " + serviceClass.getSimpleName());
        }
        return service;
    }
    
    public boolean hasService(Class<?> serviceClass) {
        return services.containsKey(serviceClass);
    }
    
    public void clear() {
        services.clear();
        logger.info("Todos os serviços foram removidos");
    }
}