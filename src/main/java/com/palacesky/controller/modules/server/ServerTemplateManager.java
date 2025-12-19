package com.palacesky.controller.modules.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palacesky.controller.modules.server.data.ServerTemplate;
import com.palacesky.controller.modules.server.data.ServerType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerTemplateManager {

    private final Logger logger;
    private final File dataFolder;
    private final Map<ServerType, ServerTemplate> templates = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public ServerTemplateManager(File dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    public void load() {
        File file = new File(dataFolder, "server-templates.json");
        if (!file.exists()) {
            logger.warning("[TemplateManager] server-templates.json não encontrado. O AutoScaler não funcionará.");
            return;
        }

        try {
            List<ServerTemplate> loadedList = mapper.readValue(file, new TypeReference<List<ServerTemplate>>() {});
            templates.clear();
            for (ServerTemplate template : loadedList) {
                templates.put(template.getType(), template);
            }
            logger.info("[TemplateManager] Carregados " + templates.size() + " templates.");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "[TemplateManager] Erro ao carregar templates", e);
        }
    }

    public ServerTemplate getTemplate(ServerType type) {
        return templates.get(type);
    }
}