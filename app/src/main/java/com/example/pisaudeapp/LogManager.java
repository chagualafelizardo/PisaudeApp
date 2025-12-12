package com.example.pisaudeapp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogManager {
    private static LogManager instance;
    private final List<String> logs = new ArrayList<>();
    private final List<LogUpdateListener> listeners = new ArrayList<>();
    private static final int MAX_LOGS = 1000;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public interface LogUpdateListener {
        void onLogsUpdated(String newLog);
        void onLogsCleared();
    }

    private LogManager() {}

    public static synchronized LogManager getInstance() {
        if (instance == null) {
            instance = new LogManager();
        }
        return instance;
    }

    public synchronized void addLog(String message) {
        String time = timeFormat.format(new Date());
        String logEntry = "[" + time + "] " + message;

        logs.add(0, logEntry); // Adiciona no início para mostrar os mais recentes primeiro

        // Limitar o número máximo de logs
        if (logs.size() > MAX_LOGS) {
            logs.remove(logs.size() - 1);
        }

        // Notificar todos os listeners
        for (LogUpdateListener listener : listeners) {
            listener.onLogsUpdated(logEntry);
        }
    }

    public synchronized List<String> getLogs() {
        return new ArrayList<>(logs);
    }

    public synchronized String getAllLogsAsString() {
        StringBuilder sb = new StringBuilder();
        for (String log : logs) {
            sb.append(log).append("\n");
        }
        return sb.toString();
    }

    public synchronized void clearLogs() {
        logs.clear();
        for (LogUpdateListener listener : listeners) {
            listener.onLogsCleared();
        }
    }

    public synchronized void registerListener(LogUpdateListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized void unregisterListener(LogUpdateListener listener) {
        listeners.remove(listener);
    }
}