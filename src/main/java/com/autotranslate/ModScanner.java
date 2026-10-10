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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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

    /** 最近一次扫描到的模组 id 集合（供缺失键按模组归组、status 展示） */
    private static volatile Set<String> scannedModIds = Set.of();

    /** 最近一次扫描到的模组 id（排序后的只读视图，供日志/诊断） */
    public static Set<String> getScannedModIds() {
        return scannedModIds;
    }

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
        List<String> scannedIds = new ArrayList<>();
        for (Map.Entry<String, Path> entry : mods.entrySet()) {
            String modId = entry.getKey();
            Path jar = entry.getValue();
            scannedIds.add(modId);

            Map<String, String> zhByKey = new HashMap<>();
            // 1. 模组自带 zh_cn（最高优先级）。
            //    扫描 jar 内所有资源命名空间 assets/*/lang/zh_cn.* —— 兼容 modid 与资源命名空间
            //    不一致的模组（如 cloth-config 的命名空间是 cloth-config2）、嵌套库等一切情况。
            Map<String, String> selfZh = readModAllLangJson(jar, "zh_cn.json");
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
                //    （同样按所有命名空间扫描，保证与 zh 的命名空间对应）
                Map<String, String> enUs = readModAllLangJson(jar, "en_us.json");
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

        scannedModIds = Set.copyOf(scannedIds);
        LOGGER.info("[自动汉化] 模组扫描完成：共 {} 个模组，{} 个有翻译，新增 {} 条翻译键 + {} 条原文匹配",
                modCount, translated, addedKey, addedText);
        LOGGER.info("[自动汉化] 扫描到的模组清单（{} 个）：{}", scannedIds.size(),
                String.join(", ", new TreeSet<>(scannedIds)));
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

    /** 扫描 jar 内所有资源命名空间的指定语言文件并合并（assets/&lt;任意&gt;/lang/&lt;file&gt;）。
     *  解决 modid 与资源命名空间不一致（如 cloth-config 的命名空间为 cloth-config2）、
     *  jar 内含多个命名空间（模组 + 内置库）等情况。 */
    private static Map<String, String> readModAllLangJson(Path jar, String file) {
        Map<String, String> merged = new HashMap<>();
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            var entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                if (!name.startsWith("assets/") || !name.endsWith("/lang/" + file) || e.isDirectory()) {
                    continue;
                }
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(zf.getInputStream(e), StandardCharsets.UTF_8))) {
                    JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
                    obj.entrySet().forEach(en -> {
                        if (en.getValue().isJsonPrimitive()) {
                            merged.put(en.getKey(), en.getValue().getAsString());
                        }
                    });
                } catch (Exception ignore) {
                    // 单个语言文件损坏时跳过，继续读其他命名空间
                }
            }
        } catch (Exception ex) {
            return null;
        }
        return merged.isEmpty() ? null : merged;
    }

    /** 从 CFPA 数据读取指定模组的 zh_cn.lang / zh_cn.json（扁平 key -> value）。
     *  数据源优先级：外部 CFPA 包（config/autotranslate/ 下所有 cfpa*.zip，用户可放更新的全量包） > 内置资源 */
    private static Map<String, String> readCfpaLang(String modId) {
        // 1. 外部 CFPA 包（config/autotranslate/cfpa_mods.zip 及目录下任意 cfpa*.zip）——用户可随时更新
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("autotranslate");
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "cfpa*.zip")) {
                for (Path zip : ds) {
                    Map<String, String> map = readCfpaFromZip(zip, modId);
                    if (map != null) {
                        return map;
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("[自动汉化] 扫描外部 CFPA 包失败: {}", e.toString());
            }
        }
        // 2. 内置 CFPA 包
        try (InputStream in = ModScanner.class.getResourceAsStream(CFPA_ZIP_RESOURCE)) {
            if (in == null) {
                LOGGER.warn("[自动汉化] 内置 CFPA 翻译数据缺失: {}", CFPA_ZIP_RESOURCE);
                return null;
            }
            return readCfpaFromStream(in, modId);
        } catch (Exception e) {
            LOGGER.warn("[自动汉化] 读取 CFPA 翻译失败 {}: {}", modId, e.toString());
        }
        return null;
    }

    /** 从 CFPA zip 路径读取指定模组翻译 */
    private static Map<String, String> readCfpaFromZip(Path zipPath, String modId) {
        try (ZipFile zf = new ZipFile(zipPath.toFile())) {
            ZipEntry lang = zf.getEntry("assets/" + modId + "/lang/zh_cn.lang");
            ZipEntry json = zf.getEntry("assets/" + modId + "/lang/zh_cn.json");
            if (lang != null) {
                try (InputStream in = zf.getInputStream(lang)) {
                    return parseLang(in);
                }
            }
            if (json != null) {
                try (InputStream in = zf.getInputStream(json)) {
                    return parseJsonLang(in);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[自动汉化] 外部 CFPA 包读取失败 {}: {}", zipPath, e.toString());
        }
        return null;
    }

    /** 从内置 CFPA 资源流读取指定模组翻译 */
    private static Map<String, String> readCfpaFromStream(InputStream in, String modId) throws IOException {
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
