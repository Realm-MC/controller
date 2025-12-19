package com.palacesky.controller.modules.server.data;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
public class ServerTemplate {
    private ServerType type;
    private int memory;
    private int disk;
    private int cpu;
    private int eggId;
    private int nestId;
    private String dockerImage;
    private String startupCommand;
    private ScalingRules scaling;
    private Map<String, String> environment;
}