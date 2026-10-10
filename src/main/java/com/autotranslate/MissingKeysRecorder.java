package com.autotranslate;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 缺失翻译键记录器：
 * 把运行时遇到的、词典未覆盖的文本（键缺失的 key，或英文原文）收集起来，
 * 定期写入 config/autotranslate/dict/missing_zh_cn.txt（每行：翻译键=英文原文），
 * 方便用户将文件改名 .lang / .json 后放入词典目录自制翻译，或反馈给开发者补全。
 */
public final class MissingKeysRecorder {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoTranslateMod.MOD_ID);
    /** 去重记录：key -> 英文原文（LinkedHashMap 保持发现顺序） */
    private static final Map<String, String> MISSING = new LinkedHashMap<>();
    /** 深度汉化收集的硬编码英文文本（不走翻译系统的），单独导出 */
    private static final java.util.Set<String> HARDCODED = new java.util.LinkedHashSet<>();
    /** 单次会话最多记录条数，防止文件无限膨胀 */
    private static final int MAX_RECORDS = 5000;
    private static final int MAX_HARDCODED = 2000;
    private static volatile ScheduledExecutorService scheduler;
    private static volatile boolean dirty = false;

    private MissingKeysRecorder() {
    }

    /** 记录一条未覆盖文本。key 为空时用英文原文本身作为标识。 */
    public static synchronized void record(String key, String englishText) {
        if (!ModConfig.isExportMissing() || MISSING.size() >= MAX_RECORDS) {
            return;
        }
        String id = (key != null && !key.isEmpty()) ? key : englishText;
        String existing = MISSING.get(id);
        if (existing == null || existing.isEmpty()) {
            MISSING.put(id, englishText == null ? "" : englishText);
            dirty = true;
        }
    }

    /** 记录一条深度汉化发现的硬编码英文文本（无翻译键，走渲染层） */
    public static synchronized void recordHardcoded(String englishText) {
        if (englishText == null || englishText.isEmpty() || HARDCODED.size() >= MAX_HARDCODED) {
            return;
        }
        if (HARDCODED.add(englishText)) {
            dirty = true;
        }
    }

    /** 启动定时导出（由客户端初始化调用一次） */
    public static void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "autotranslate-missing-writer");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(MissingKeysRecorder::flushIfDirty, 30, 30, TimeUnit.SECONDS);
    }

    /** 退出前/重载时同步落盘 */
    public static synchronized void flush() {
        if (!dirty) {
            return;
        }
        boolean any = !MISSING.isEmpty() || !HARDCODED.isEmpty();
        if (!any) {
            dirty = false;
            return; // 本次会话无缺失，不生成文件
        }
        try {
            Path dir = ModConfig.getExternalDictDir();
            if (dir.getParent() != null) {
                Files.createDirectories(dir);
            }
            if (!MISSING.isEmpty()) {
                Path file = dir.resolve("missing_zh_cn.txt");
                StringBuilder sb = new StringBuilder();
                sb.append("# 自动汉化模组 - 未覆盖翻译键导出\n");
                sb.append("# 每行格式：翻译键=英文原文（与 .lang 词典格式一致）\n");
                sb.append("# 使用方法：把本文件改名 xxx.lang 或把内容整理为 {\"byKey\":{...}} 的 xxx.json，放入本目录即生效\n");
                sb.append("# 也可直接发给开发者，用于内置补全\n\n");
                for (Map.Entry<String, String> e : MISSING.entrySet()) {
                    sb.append(e.getKey()).append('=').append(e.getValue().replace("\n", "\\n")).append('\n');
                }
                Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            }
            if (!HARDCODED.isEmpty()) {
                Path file = dir.resolve("missing_hardcoded.txt");
                StringBuilder sb = new StringBuilder();
                sb.append("# 自动汉化模组 - 硬编码英文文本（不走翻译系统的，深度汉化收集）\n");
                sb.append("# 每行一个英文原文。请发给开发者内置进 byText 词典，之后自动生效\n\n");
                for (String t : HARDCODED) {
                    sb.append(t.replace("\n", "\\n")).append('\n');
                }
                Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            }
            dirty = false;
        } catch (IOException e) {
            LOGGER.warn("[自动汉化] 导出缺失键失败: {}", e.toString());
        }
    }

    private static void flushIfDirty() {
        if (dirty) {
            flush();
        }
    }

    private static Path getMissingFile() {
        return ModConfig.getExternalDictDir().resolve("missing_zh_cn.txt");
    }

    public static synchronized int count() {
        return MISSING.size();
    }
}
