package com.expleo.simulator;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.file.Paths;

public class Config {

    private static final String CONFIG_FILE_PATH = "Template/config.json";
    private static JSONObject configData = new JSONObject();

    static {
        loadConfig();
    }

    public static void loadConfig() {
        File file = new File(CONFIG_FILE_PATH);
        if (file.exists()) {
            try (InputStream is = new FileInputStream(file)) {
                JSONTokener tokener = new JSONTokener(is);
                configData = new JSONObject(tokener);
            } catch (Exception e) {
                System.err.println("Failed to load config: " + e.getMessage());
            }
        } else {
            System.err.println("Config file not found at " + file.getAbsolutePath());
        }
    }

    public static void saveConfig() {
        File file = new File(CONFIG_FILE_PATH);
        try {
            // Ensure parent directory exists
            file.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(configData.toString(4));
            }
        } catch (Exception e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    public static JSONObject getConfigData() {
        return configData;
    }

    public static Object get(String key) {
        return configData.has(key) ? configData.get(key) : null;
    }
    
    public static JSONObject getJSONObject(String key) {
        return configData.has(key) ? configData.getJSONObject(key) : new JSONObject();
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        if (configData.has(key)) {
            return configData.getBoolean(key);
        }
        return defaultValue;
    }

    public static void set(String key, Object value) {
        configData.put(key, value);
    }
}
