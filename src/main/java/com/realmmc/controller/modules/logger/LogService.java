package com.realmmc.controller.modules.logger;

import com.realmmc.controller.shared.utils.TaskScheduler;
import lombok.Getter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LogService {

    private final Logger logger;
    private final File dataFolder;
    private final String serverName;

    private BufferedWriter writer;

    private final Queue<String> logQueue = new ConcurrentLinkedQueue<>();

    @Getter
    private final String sessionCode;

    private final SimpleDateFormat logTimeFormat = new SimpleDateFormat("HH:mm:ss");
    private final SimpleDateFormat folderDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final SimpleDateFormat fileNameTimeFormat = new SimpleDateFormat("HH-mm-ss");

    private final Random random = new Random();
    private String currentFolderDate;

    private boolean running = true;

    public LogService(Logger logger, File dataFolder, String serverName) throws IOException {
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.serverName = serverName;

        this.sessionCode = generateSessionCode();

        initializeWriter();

        startFlushTask();
    }

    private void initializeWriter() throws IOException {
        Date now = new Date();
        this.currentFolderDate = folderDateFormat.format(now);

        File logsDir = new File(dataFolder, "logs" + File.separator + currentFolderDate);
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }

        String timeString = fileNameTimeFormat.format(now);
        String fileName = String.format("%s_%s_%s.log", serverName, timeString, sessionCode);

        File logFile = new File(logsDir, fileName);

        if (this.writer != null) {
            try {
                this.writer.write("[SYSTEM] Rotação de log: Mudança de dia. Continuando em novo arquivo.");
                this.writer.newLine();
                this.writer.close();
            } catch (IOException ignored) {}
        }

        this.writer = new BufferedWriter(new FileWriter(logFile, true));

        logRaw("SYSTEM", "Sessão: " + sessionCode);
        logRaw("SYSTEM", "Servidor: " + serverName);
        logRaw("SYSTEM", "Arquivo criado em: " + logTimeFormat.format(now));
        logRaw("SYSTEM", "--------------------------------------------------");
    }

    private String generateSessionCode() {
        char l1 = (char) ('A' + random.nextInt(26));
        char l2 = (char) ('A' + random.nextInt(26));
        int n1 = random.nextInt(10);
        int n2 = random.nextInt(10);
        char l3 = (char) ('A' + random.nextInt(26));
        char l4 = (char) ('A' + random.nextInt(26));
        return "" + l1 + l2 + n1 + n2 + l3 + l4;
    }

    private void startFlushTask() {
        TaskScheduler.runAsyncTimer(() -> {
            if (!running && logQueue.isEmpty()) return;

            checkDayRotation();

            flush();
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void checkDayRotation() {
        Date now = new Date();
        String todayDate = folderDateFormat.format(now);

        if (!todayDate.equals(currentFolderDate)) {
            try {
                flush();
                initializeWriter();
                logger.info("[LogService] Dia alterado. Rotação de logs efetuada para: " + todayDate);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Falha na rotação de logs (Mudança de dia)", e);
            }
        }
    }

    private void flush() {
        try {
            while (!logQueue.isEmpty()) {
                String line = logQueue.poll();
                if (line != null && writer != null) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            if (writer != null) writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void logRaw(String type, String content) {
        String timestamp = logTimeFormat.format(new Date());
        String entry = String.format("[%s] [%s] %s", timestamp, type, content);
        try {
            if (writer != null) {
                writer.write(entry);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void log(String type, String content) {
        if (!running) return;
        String timestamp = logTimeFormat.format(new Date());
        String entry = String.format("[%s] [%s] %s", timestamp, type, content);
        logQueue.add(entry);
    }

    public void shutdown() {
        log("SYSTEM", "--------------------------------------------------");
        log("SYSTEM", "Sessão encerrada.");

        flush();

        running = false;
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Erro ao fechar arquivo de log.", e);
        }
    }
}