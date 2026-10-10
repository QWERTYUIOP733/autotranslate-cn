package com.autotranslate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * 自动扫描 mods 文件夹中的其他模组并加载其汉化翻译 —— 核心功能。
 *
 * 翻译来源（优先级从高到低，低优先级不覆盖高优先级）：
 * 1. 模组 jar 自带 zh_cn.json（模组官方中文）
 * 2. 内置 CFPA（Minecraft 模组翻译包）数据：assets/autotranslate/cfpa_mods.zip
 *    （来源 CFPAOrg/Minecraft-Mod-Language-Package，CC BY-NC-SA 4.0，见 LICENSE_CFPA）
 * 3. byText 原文匹配：模组 jar 的 en_us.json 与上述中文按翻译键配对，生成"英文原文 → 中文"
 *
 * 扫描对象：mods 目录下除本模组外的所有 *.jar（自动识别 fabric.mod.json 的 mod id）。
 */
public final class ModScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoTranslateMod.MOD_ID);
    private static final String CFPA_ZIP_RESOURCE = "/assets/autotranslate/cfpa_mods.zip";
    private static final String SELF_PREFIX = "autotranslate-";

    private ModScanner() {
    }

    /** 扫描结果统计 */
    public static final class ScanResult {
        public final int modCount;
        public final int translatedMods;
        public final int addedKey;
        public final int addedText;

        ScanResult(int modCount, int translatedMods, int addedKey, int addedText) {
            this.modCount = modCount;
            this.translatedMods = translatedMods;
            this.addedKey = addedKey;
            this.addedText = addedText;
        }
    }

    /**
     * 扫描 mods 目录，把找到的翻译合并进词典（低优先级不覆盖已存在条目）。
     * 在 Dictionary.reload() 中于官方词典之后、外部词典之前调用。
     */
    public static synchronized ScanResult scanAndMerge(Map<String, String> keyMap, Map<String, String> textMap) {
        Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
        Map<String, Path> mods = new HashMap<>(); // modId -> jar 路径
        int modCount = 0;

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path jar : ds) {
                String name = jar.getFileName().toString();
                if (name.startsWith(SELF_PREFIX)) {
                    continue;
                }
                modCount++;
                String modId = readModId(jar);
                if (modId != null && !modId.equals(AutoTranslateMod.MOD_ID)) {
                    mods.put(modId, jar);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[自动汉化] 扫描 mods 目录失败: {}", e.toString());
            return new ScanResult(0, 0, 0, 0);
        }

        int translated = 0, addedKey = 0, addedText = 0;
        for (Map.Entry<String, Path> entry : mods.entrySet()) {
            String modId = entry.getKey();
            Path jar = entry.getValue();

            Map<String, String> zhByKey = new HashMap<>();
            // 1. 模组自带 zh_cn.json（最高优先级）
            Map<String, String> selfZh = readModLangJson(jar, modId, "zh_cn.json");
            if (selfZh != null) {
                zhByKey.putAll(selfZh);
            }
            // 2. 内置 CFPA 数据（不覆盖模组自带）
            Map<String, String> cfpaZh = readCfpaLang(modId);
            if (cfpaZh != null) {
                cfpaZh.forEach(zhByKey::putIfAbsent);
            }

            if (!zhByKey.isEmpty()) {
                translated++;
                for (Map.Entry<String, String> zh : zhByKey.entrySet()) {
                    if (keyMap.putIfAbsent(zh.getKey(), zh.getValue()) == null) {
                        addedKey++;
                    }
                }
                // 3. byText：en_us.json 与中文按翻译键配对 → 英文原文匹配
                Map<String, String> enUs = readModLangJson(jar, modId, "en_us.json");
                if (enUs != null) {
                    for (Map.Entry<String, String> en : enUs.entrySet()) {
                        String zh = zhByKey.get(en.getKey());
                        if (zh != null && textMap.putIfAbsent(en.getValue(), zh) == null) {
                            addedText++;
                        }
                    }
                }
            }
        }

        LOGGER.info("[自动汉化] 模组扫描完成：共 {} 个模组，{} 个有翻译，新增 {} 条翻译键 + {} 条原文匹配",
                modCount, translated, addedKey, addedText);
        return new ScanResult(modCount, translated, addedKey, addedText);
    }

    /** 读 jar 内 fabric.mod.json 的 mod id */
    private static String readModId(Path jar) {
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            ZipEntry e = zf.getEntry("fabric.mod.json");
            if (e == null) {
                return null;
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(zf.getInputStream(e), StandardCharsets.UTF_8))) {
                JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
                if (obj.has("id") && obj.get("id").isJsonPrimitive()) {
                    return obj.get("id").getAsString();
                }
            }
        } catch (Exception ex) {
            // 单个 jar 解析失败时跳过该模组
        }
        return null;
    }

    /** 读模组 jar 内 assets/&lt;modId&gt;/lang/&lt;file&gt;.json（扁平 key -> value） */
    private static Map<String, String> readModLangJson(Path jar, String modId, String file) {
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            ZipEntry e = zf.getEntry("assets/" + modId + "/lang/" + file);
            if (e == null) {
                return null;
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(zf.getInputStream(e), StandardCharsets.UTF_8))) {
                JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
                Map<String, String> map = new HashMap<>();
                obj.entrySet().forEach(en -> {
                    if (en.getValue().isJsonPrimitive()) {
                        map.put(en.getKey(), en.getValue().getAsString());
                    }
                });
                return map;
            }
        } catch (Exception ex) {
            return null;
        }
    }

    /** 从内置 CFPA zip 读取指定模组的 zh_cn.lang / zh_cn.json（扁平 key -> value） */
    private static Map<String, String> readCfpaLang(String modId) {
        try (InputStream in = ModScanner.class.getResourceAsStream(CFPA_ZIP_RESOURCE)) {
            if (in == null) {
                LOGGER.warn("[自动汉化] 内置 CFPA 翻译数据缺失: {}", CFPA_ZIP_RESOURCE);
                return null;
            }
            String langPath = "assets/" + modId + "/lang/zh_cn.lang";
            String jsonPath = "assets/" + modId + "/lang/zh_cn.json";
            try (ZipInputStream zis = new ZipInputStream(in)) {
                ZipEntry ze;
                while ((ze = zis.getNextEntry()) != null) {
                    String name = ze.getName();
                    if (name.equals(langPath)) {
                        return parseLang(zis);
                    }
                    if (name.equals(jsonPath)) {
                        return parseJsonLang(zis);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[自动汉化] 读取 CFPA 翻译失败 {}: {}", modId, e.toString());
        }
        return null;
    }

    /** 解析 .lang 格式（每行 key=value，UTF-8，跳过空行与 # 注释） */
    private static Map<String, String> parseLang(InputStream in) throws IOException {
        Map<String, String> map = new HashMap<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq > 0) {
                    map.put(line.substring(0, eq), line.substring(eq + 1));
                }
            }
        }
        return map;
    }

    /** 解析扁平 JSON 语言文件 */
    private static Map<String, String> parseJsonLang(InputStream in) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
            Map<String, String> map = new HashMap<>();
            obj.entrySet().forEach(en -> {
                if (en.getValue().isJsonPrimitive()) {
                    map.put(en.getKey(), en.getValue().getAsString());
                }
            });
            return map;
        }
    }
}
