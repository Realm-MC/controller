package com.realmmc.controller.modules.cash;

import com.realmmc.controller.core.modules.AbstractCoreModule;
import com.realmmc.controller.core.modules.AutoRegister;
import com.realmmc.controller.core.services.ServiceRegistry;
import com.realmmc.controller.shared.cash.CashService;

import java.util.logging.Logger;

@AutoRegister(platforms = {AutoRegister.Platform.PROXY})
public class CashModule extends AbstractCoreModule {

    private CashService cashService;

    public CashModule(Logger logger) {
        super(logger);
    }

    @Override
    public String getName() {
        return "CashModule";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Gerencia a economia de Cash e o cache do Top 10.";
    }

    @Override
    public String[] getDependencies() {
        return new String[]{"Profile", "SchedulerModule"};
    }

    @Override
    public int getPriority() {
        return 35;
    }

    @Override
    protected void onEnable() throws Exception {
        this.cashService = new CashService();
        ServiceRegistry.getInstance().registerService(CashService.class, this.cashService);
        this.cashService.startCacheTask();
    }

    @Override
    protected void onDisable() throws Exception {
        ServiceRegistry.getInstance().unregisterService(CashService.class);
    }
}