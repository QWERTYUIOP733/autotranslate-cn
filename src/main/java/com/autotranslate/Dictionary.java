package com.autotranslate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 翻译词典：
 * - byKey  ：翻译键 → 中文（针对走 translatable 组件的模组文本）
 * - byText ：英文原文 → 中文（针对直接写死英文文本的场景）
 *
 * 数据来源：
 * 1. 内置：assets/autotranslate/lang/zh_cn_dict.json（骨架词典）
 * 2. 外部：config/autotranslate/dict/ 目录下所有 .json（用户可放入完整汉化数据）
 */
public final class Dictionary {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoTranslateMod.MOD_ID);
    private static final String BUILTIN_DICT = "/assets/autotranslate/lang/zh_cn_dict.json";
    private static final String EXTRA_DICT = "/assets/autotranslate/lang/extra_zh_cn.json";

    private static volatile Map<String, String> byKey = Map.of();
    private static volatile Map<String, String> byText = Map.of();

    private Dictionary() {
    }

    /** 加载内置 + 模组扫描 + 外部词典（幂等，可随时重载） */
    public static synchronized void reload() {
        Map<String, String> newKey = new HashMap<>();
        Map<String, String> newText = new HashMap<>();

        loadBuiltin(newKey, newText);
        ModScanner.scanAndMerge(newKey, newText);
        loadExternal(newKey, newText);

        byKey = Map.copyOf(newKey);
        byText = Map.copyOf(newText);
        LOGGER.info("[自动汉化] 词典加载完成：byKey={} 条，byText={} 条", byKey.size(), byText.size());
    }

    private static void loadBuiltin(Map<String, String> keyMap, Map<String, String> textMap) {
        loadJsonResource(BUILTIN_DICT, keyMap, textMap);
        loadJsonResource(EXTRA_DICT, keyMap, textMap);
    }

    /** 从 classpath 读取扁平/结构式 JSON 词典 */
    private static void loadJsonResource(String resource, Map<String, String> keyMap, Map<String, String> textMap) {
        try (InputStream in = Dictionary.class.getResourceAsStream(resource)) {
            if (in == null) {
                LOGGER.warn("[自动汉化] 内置词典缺失: {}", resource);
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                merge(obj, keyMap, textMap);
            }
        } catch (Exception e) {
            LOGGER.warn("[自动汉化] 内置词典解析失败 {}: {}", resource, e.toString());
        }
    }

    private static void loadExternal(Map<String, String> keyMap, Map<String, String> textMap) {
        Path dir = ModConfig.getExternalDictDir();
        try {
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir);
                return;
            }
            // 支持 .json（扁平/结构式）与 .lang（key=value 行）两种格式
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.{json,lang}")) {
                for (Path p : stream) {
                    try {
                        String name = p.getFileName().toString();
                        if (name.endsWith(".lang")) {
                            Map<String, String> flat = new HashMap<>();
                            try (BufferedReader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                                String line;
                                while ((line = r.readLine()) != null) {
                                    if (line.isEmpty() || line.startsWith("#")) {
                                        continue;
                                    }
                                    int eq = line.indexOf('=');
                                    if (eq > 0) {
                                        flat.put(line.substring(0, eq), line.substring(eq + 1));
                                    }
                                }
                            }
                            keyMap.putAll(flat);
                            LOGGER.info("[自动汉化] 已加载外部词典(.lang): {}", p.getFileName());
                        } else {
                            JsonObject obj = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
                            merge(obj, keyMap, textMap);
                            LOGGER.info("[自动汉化] 已加载外部词典: {}", p.getFileName());
                        }
                    } catch (Exception e) {
                        LOGGER.warn("[自动汉化] 外部词典解析失败 {}: {}", p.getFileName(), e.toString());
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[自动汉化] 外部词典目录不可用 {}: {}", dir, e.toString());
        }
    }

    /**
     * 合并一份词典 JSON。支持两种格式：
     * - 结构式：{"byKey": {...}, "byText": {...}}
     * - 扁平式：{"翻译键": "中文"}（自动归入 byKey）
     */
    private static void merge(JsonObject obj, Map<String, String> keyMap, Map<String, String> textMap) {
        if (obj.has("byKey") && obj.get("byKey").isJsonObject()) {
            mergeInto(keyMap, obj.getAsJsonObject("byKey"));
        }
        if (obj.has("byText") && obj.get("byText").isJsonObject()) {
            mergeInto(textMap, obj.getAsJsonObject("byText"));
        }
        // 扁平格式：既无 byKey 也无 byText 时整体按 byKey 处理
        if (!obj.has("byKey") && !obj.has("byText")) {
            mergeInto(keyMap, obj);
        }
    }

    private static void mergeInto(Map<String, String> target, JsonObject src) {
        src.entrySet().forEach(e -> {
            if (e.getValue().isJsonPrimitive()) {
                target.put(e.getKey(), e.getValue().getAsString());
            }
        });
    }

    /** 按翻译键查中文；未命中返回 null */
    public static String lookupKey(String key) {
        return key == null ? null : byKey.get(key);
    }

    /** 按英文原文查中文；未命中返回 null */
    public static String lookupText(String text) {
        return text == null ? null : byText.get(text);
    }

    /** 词典总条数 */
    public static int size() {
        return byKey.size() + byText.size();
    }
}
