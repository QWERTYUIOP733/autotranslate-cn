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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    /** 小写归一化索引：byText 的英文原文忽略大小写也能命中（如 "iron sword" -> "Iron Sword"） */
    private static volatile Map<String, String> byTextLower = Map.of();

    /** Minecraft 术语表（英文词 -> 规范中文）：在线翻译结果中残留的英文专有名词/常用词会被替换。
     *  仅按整词边界替换译文里的英文单词，不影响中文部分。 */
    private static final Map<String, String> TERMINOLOGY = buildTerminology();

    private static Map<String, String> buildTerminology() {
        Map<String, String> m = new HashMap<>();
        // 生物
        m.put("creeper", "苦力怕"); m.put("enderman", "末影人"); m.put("zombie", "僵尸");
        m.put("skeleton", "骷髅"); m.put("spider", "蜘蛛"); m.put("slime", "史莱姆");
        m.put("villager", "村民"); m.put("wither", "凋灵"); m.put("dragon", "龙");
        m.put("phantom", "幻翼"); m.put("blaze", "烈焰人"); m.put("ghast", "恶魂");
        // 矿物/材料
        m.put("iron", "铁"); m.put("gold", "金"); m.put("diamond", "钻石"); m.put("emerald", "绿宝石");
        m.put("redstone", "红石"); m.put("quartz", "石英"); m.put("lapis", "青金石");
        m.put("netherite", "下界合金"); m.put("obsidian", "黑曜石"); m.put("stone", "石头");
        m.put("wooden", "木制"); m.put("leather", "皮革"); m.put("copper", "铜"); m.put("coal", "煤");
        // 装备/工具
        m.put("sword", "剑"); m.put("pickaxe", "镐"); m.put("axe", "斧"); m.put("shovel", "锹");
        m.put("hoe", "锄"); m.put("bow", "弓"); m.put("arrow", "箭"); m.put("armor", "盔甲");
        m.put("helmet", "头盔"); m.put("chestplate", "胸甲"); m.put("leggings", "护腿"); m.put("boots", "靴子");
        m.put("shield", "盾牌");
        // 方块/机制
        m.put("chest", "箱子"); m.put("furnace", "熔炉"); m.put("crafting", "合成");
        m.put("enchantment", "附魔"); m.put("potion", "药水");
        // 属性
        m.put("damage", "伤害"); m.put("health", "生命"); m.put("attack", "攻击"); m.put("defense", "防御");
        m.put("speed", "速度"); m.put("jump", "跳跃"); m.put("chance", "几率"); m.put("level", "等级");
        m.put("modifier", "加成"); m.put("cooldown", "冷却"); m.put("duration", "持续时间");
        m.put("range", "范围"); m.put("radius", "半径"); m.put("spawn", "生成"); m.put("respawn", "重生");
        return m;
    }

    /** 对在线翻译结果做术语后处理：把残留的英文单词替换为规范中文（整词边界匹配）。 */
    public static String postProcessTranslation(String zh) {
        if (zh == null || zh.isEmpty()) {
            return zh;
        }
        StringBuilder sb = new StringBuilder(zh.length() + 16);
        int i = 0;
        while (i < zh.length()) {
            char c = zh.charAt(i);
            if (isAsciiLetter(c)) {
                int j = i;
                while (j < zh.length() && isAsciiLetter(zh.charAt(j))) {
                    j++;
                }
                String word = zh.substring(i, j).toLowerCase(Locale.ROOT);
                String repl = TERMINOLOGY.get(word);
                sb.append(repl != null ? repl : zh.substring(i, j));
                i = j;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /** 是否包含中文字符（用于在线翻译结果质量校验：译文应含中文才算成功） */
    public static boolean isChinese(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                return true;
            }
        }
        return false;
    }

    /** 在线翻译结果缓冲：翻译线程写入，定时线程批量落盘（避免翻译风暴时每次全量 IO） */
    private static final Map<String, String> PENDING_CACHE = new HashMap<>();
    private static final ScheduledExecutorService CACHE_FLUSHER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "autotranslate-cache-flush");
        t.setDaemon(true);
        return t;
    });

    static {
        CACHE_FLUSHER.scheduleWithFixedDelay(Dictionary::flushCache, 5, 5, TimeUnit.SECONDS);
    }

    private Dictionary() {
    }

    /** 加载内置 + 模组扫描 + 外部词典（幂等，可随时重载） */
    public static synchronized void reload() {
        Map<String, String> newKey = new HashMap<>();
        Map<String, String> newText = new HashMap<>();

        int builtin = loadBuiltin(newKey, newText);
        ModScanner.ScanResult scan = ModScanner.scanAndMerge(newKey, newText);
        int external = loadExternal(newKey, newText);

        byKey = Map.copyOf(newKey);
        byText = Map.copyOf(newText);
        byTextLower = buildLowerIndex(newText);
        LOGGER.info("[自动汉化] 词典加载完成：byKey={} 条，byText={} 条（内置 {} 条 / 模组扫描 {} 模组 +{} 键 / 外部 {} 条）",
                byKey.size(), byText.size(), builtin, scan.modCount, scan.addedKey, external);
    }

    private static Map<String, String> buildLowerIndex(Map<String, String> textMap) {
        Map<String, String> lower = new HashMap<>();
        textMap.forEach((en, zh) -> lower.put(en.toLowerCase(Locale.ROOT), zh));
        return Map.copyOf(lower);
    }

    private static int loadBuiltin(Map<String, String> keyMap, Map<String, String> textMap) {
        int before = keyMap.size() + textMap.size();
        loadJsonResource(BUILTIN_DICT, keyMap, textMap);
        loadJsonResource(EXTRA_DICT, keyMap, textMap);
        return (keyMap.size() + textMap.size()) - before;
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

    private static int loadExternal(Map<String, String> keyMap, Map<String, String> textMap) {
        Path dir = ModConfig.getExternalDictDir();
        int added = 0;
        try {
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir);
                return 0;
            }
            // 支持 .json（扁平/结构式）与 .lang（key=value 行）两种格式；
            // 支持一层子目录递归（便于按模组分类管理词典）
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.{json,lang}")) {
                for (Path p : stream) {
                    added += loadExternalFile(p, keyMap, textMap);
                }
            }
            try (DirectoryStream<Path> subDirs = Files.newDirectoryStream(dir)) {
                for (Path sub : subDirs) {
                    if (Files.isDirectory(sub)) {
                        try (DirectoryStream<Path> subStream = Files.newDirectoryStream(sub, "*.{json,lang}")) {
                            for (Path p : subStream) {
                                added += loadExternalFile(p, keyMap, textMap);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[自动汉化] 外部词典目录不可用 {}: {}", dir, e.toString());
        }
        return added;
    }

    private static int loadExternalFile(Path p, Map<String, String> keyMap, Map<String, String> textMap) {
        int before = keyMap.size() + textMap.size();
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
                LOGGER.info("[自动汉化] 已加载外部词典(.lang): {}", p);
            } else {
                JsonObject obj = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
                merge(obj, keyMap, textMap);
                LOGGER.info("[自动汉化] 已加载外部词典: {}", p);
            }
        } catch (Exception e) {
            LOGGER.warn("[自动汉化] 外部词典解析失败 {}: {}", p.getFileName(), e.toString());
        }
        return (keyMap.size() + textMap.size()) - before;
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

    /** 按英文原文查中文；未命中返回 null。
     *  先精确匹配，再小写归一化匹配，最后剥离尾部省略号"..."后再试一次
     *  （GUI 列表项被截断渲染时文本为 "Aquatic Lasso..."，词典存 "Aquatic Lasso"）。 */
    public static String lookupText(String text) {
        if (text == null) {
            return null;
        }
        String zh = byText.get(text);
        if (zh == null) {
            zh = byTextLower.get(text.toLowerCase(Locale.ROOT));
        }
        if (zh == null && text.endsWith("...")) {
            String core = text.substring(0, text.length() - 3);
            zh = byText.get(core);
            if (zh == null) {
                zh = byTextLower.get(core.toLowerCase(Locale.ROOT));
            }
        }
        return zh;
    }

    /** 词典总条数 */
    public static int size() {
        return byKey.size() + byText.size();
    }

    /**
     * 运行时新增一条 byText 翻译（英文原文 -> 中文）。
     * 供在线翻译兜底使用：合并进内存词典，并缓冲追加到 cache.json
     * （定时批量落盘），使下次启动（外部词典加载阶段）自动生效。
     */
    public static synchronized void addTextEntry(String englishText, String zh) {
        if (englishText == null || zh == null || englishText.isEmpty()) {
            return;
        }
        Map<String, String> newText = new HashMap<>(byText);
        if (zh.equals(newText.get(englishText))) {
            return; // 已有相同译文
        }
        newText.put(englishText, zh);
        byText = Map.copyOf(newText);
        Map<String, String> lower = new HashMap<>(byTextLower);
        lower.put(englishText.toLowerCase(Locale.ROOT), zh);
        byTextLower = Map.copyOf(lower);
        synchronized (PENDING_CACHE) {
            PENDING_CACHE.put(englishText, zh);
        }
    }

    /** 把缓冲的在线翻译结果合并写入 cache.json（由定时线程调用） */
    private static void flushCache() {
        Map<String, String> batch;
        synchronized (PENDING_CACHE) {
            if (PENDING_CACHE.isEmpty()) {
                return;
            }
            batch = new HashMap<>(PENDING_CACHE);
            PENDING_CACHE.clear();
        }
        try {
            Path dir = ModConfig.getExternalDictDir();
            Files.createDirectories(dir);
            Path file = dir.resolve("cache.json");
            Map<String, String> map = new HashMap<>();
            if (Files.exists(file)) {
                try {
                    JsonObject obj = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                    if (obj.has("byText") && obj.get("byText").isJsonObject()) {
                        mergeInto(map, obj.getAsJsonObject("byText"));
                    }
                } catch (Exception ignored) {
                    // 缓存文件损坏时重建
                }
            }
            map.putAll(batch);
            JsonObject out = new JsonObject();
            JsonObject byTextObj = new JsonObject();
            map.forEach(byTextObj::addProperty);
            out.add("byText", byTextObj);
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
            LOGGER.info("[自动汉化] 在线翻译缓存已落盘：新增 {} 条，共 {} 条", batch.size(), map.size());
        } catch (IOException e) {
            LOGGER.warn("[自动汉化] 写入在线翻译缓存失败: {}", e.toString());
        }
    }
}
