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
import java.util.ArrayList;
import java.util.List;

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
    /** 在线翻译兜底：词典未覆盖的英文文本自动联网翻译（异步，结果缓存到本地词典） */
    private static boolean onlineTranslate = false;
    /** 在线翻译服务地址（URL 模板：%s 会被替换为 URL 编码后的英文文本） */
    private static String translateServiceUrl =
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=zh-CN&dt=t&q=%s";
    /** 翻译引擎：auto=按响应自动识别 google/argos；google=Google 免费接口；argos=本地 Argos Translate 服务 */
    private static String translateEngine = "auto";
    /** 导出未覆盖翻译键：把运行时遇到的未翻译文本记录到 missing_zh_cn.txt，便于用户反馈/自译 */
    private static boolean exportMissing = true;
    /** 深度汉化（渲染层拦截硬编码文本）：0=关 1=词典覆盖（只翻已收录） 2=全自动（未收录短文本也联网翻译） */
    private static int deepTranslate = 0;

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
                if (obj.has("onlineTranslate")) {
                    onlineTranslate = obj.get("onlineTranslate").getAsBoolean();
                }
                if (obj.has("translateServiceUrl")) {
                    translateServiceUrl = obj.get("translateServiceUrl").getAsString();
                }
                if (obj.has("translateEngine")) {
                    translateEngine = obj.get("translateEngine").getAsString();
                }
                if (obj.has("exportMissing")) {
                    exportMissing = obj.get("exportMissing").getAsBoolean();
                }
                if (obj.has("deepTranslate")) {
                    deepTranslate = obj.get("deepTranslate").getAsInt();
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[自动汉化] 读取配置文件失败，使用默认配置: {}", e.toString());
            enabled = true;
            overrideExisting = false;
            externalDictDir = DEFAULT_EXTERNAL_DIR;
            onlineTranslate = false;
            exportMissing = true;
            deepTranslate = 0;
            translateEngine = "auto";
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
            obj.addProperty("onlineTranslate", onlineTranslate);
            obj.addProperty("translateServiceUrl", translateServiceUrl);
            obj.addProperty("translateEngine", translateEngine);
            obj.addProperty("exportMissing", exportMissing);
            obj.addProperty("deepTranslate", deepTranslate);
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

    public static boolean isOnlineTranslate() {
        return onlineTranslate;
    }

    public static void setOnlineTranslate(boolean value) {
        onlineTranslate = value;
    }

    public static String getTranslateServiceUrl() {
        return translateServiceUrl;
    }

    /** 配置界面等运行时修改入口：翻译服务地址（支持分号分隔多引擎） */
    public static void setTranslateServiceUrl(String value) {
        translateServiceUrl = value;
    }

    /** 翻译服务地址列表：支持分号/逗号分隔多个服务（多引擎 failover，第一个成功即用）。
     *  每个地址模板需包含 %s 参数位。 */
    public static List<String> getTranslateServiceUrls() {
        List<String> urls = new ArrayList<>();
        for (String u : translateServiceUrl.split("[;,]")) {
            String t = u.trim();
            if (!t.isEmpty()) {
                urls.add(t);
            }
        }
        return urls.isEmpty() ? List.of(translateServiceUrl) : urls;
    }

    public static String getTranslateEngine() {
        return translateEngine;
    }

    public static void setTranslateEngine(String value) {
        translateEngine = value;
    }

    public static boolean isExportMissing() {
        return exportMissing;
    }

    /** 配置界面等运行时修改入口 */
    public static void setExportMissing(boolean value) {
        exportMissing = value;
    }

    /** 深度汉化档位：0=关 1=词典覆盖 2=全自动 */
    public static int getDeepTranslate() {
        return deepTranslate;
    }

    public static void setDeepTranslate(int value) {
        deepTranslate = value;
    }

    /** 深度汉化是否开启 */
    public static boolean isDeepTranslateOn() {
        return deepTranslate > 0;
    }

    /** 深度汉化是否全自动（未收录的短英文文本也联网翻译） */
    public static boolean isDeepTranslateAuto() {
        return deepTranslate >= 2;
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
                + "，在线翻译=" + (onlineTranslate ? "开" : "关")
                + "，深度汉化=" + (deepTranslate == 0 ? "关" : deepTranslate == 1 ? "词典覆盖" : "全自动")
                + "，导出缺失键=" + (exportMissing ? "开" : "关")
                + "，外部词典目录=" + externalDictDir;
    }
}
