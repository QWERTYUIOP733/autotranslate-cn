package com.autotranslate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模组配置文件：config/autotranslate/config.json
 */
public final class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoTranslateMod.MOD_ID);
    private static final String DEFAULT_EXTERNAL_DIR = "autotranslate/dict";

    /** 总开关：false 时完全放行原版逻辑 */
    private static boolean enabled = true;
    /** true=词典优先，命中即覆盖原版/其他模组翻译；false=仅补缺（原版没有翻译时才用词典） */
    private static boolean overrideExisting = false;
    /** 外部词典目录（相对 config 目录） */
    private static String externalDictDir = DEFAULT_EXTERNAL_DIR;

    private ModConfig() {
    }

    public static void load() {
        Path file = getConfigFile();
        try {
            if (Files.exists(file)) {
                JsonObject obj = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                if (obj.has("enabled")) {
                    enabled = obj.get("enabled").getAsBoolean();
                }
                if (obj.has("overrideExisting")) {
                    overrideExisting = obj.get("overrideExisting").getAsBoolean();
                }
                if (obj.has("externalDictDir")) {
                    externalDictDir = obj.get("externalDictDir").getAsString();
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[自动汉化] 读取配置文件失败，使用默认配置: {}", e.toString());
            enabled = true;
            overrideExisting = false;
            externalDictDir = DEFAULT_EXTERNAL_DIR;
        }
        save();
    }

    public static void save() {
        try {
            Path file = getConfigFile();
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("enabled", enabled);
            obj.addProperty("overrideExisting", overrideExisting);
            obj.addProperty("externalDictDir", externalDictDir);
            Files.writeString(file, obj.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("[自动汉化] 写入配置文件失败: {}", e.toString());
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isOverrideExisting() {
        return overrideExisting;
    }

    /** 供配置界面等运行时修改入口使用 */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /** 供配置界面等运行时修改入口使用 */
    public static void setOverrideExisting(boolean value) {
        overrideExisting = value;
    }

    /** 外部词典目录（绝对路径） */
    public static Path getExternalDictDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(externalDictDir);
    }

    private static Path getConfigFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("autotranslate/config.json");
    }

    public static String statusText() {
        return "总开关=" + (enabled ? "开" : "关")
                + "，覆盖策略=" + (overrideExisting ? "词典优先" : "仅补缺")
                + "，外部词典目录=" + externalDictDir;
    }
}
