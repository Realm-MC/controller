package com.palacesky.controller.modules.logger;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.config.Property;

@Plugin(name = "PalaceLogAppender", category = "Core", elementType = "appender", printObject = true)
public class PalaceLogAppender extends AbstractAppender {

    private final LogService logService;

    public PalaceLogAppender(LogService logService) {
        super("PalaceLogAppender", null, PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY);
        this.logService = logService;
    }

    @Override
    public void append(LogEvent event) {
        if (logService == null) return;

        String message = event.getMessage().getFormattedMessage();
        String level = event.getLevel().name();

        if (!message.contains("[PalaceLogAppender]")) {
            logService.log("CONSOLE-" + level, message);
        }
    }
}