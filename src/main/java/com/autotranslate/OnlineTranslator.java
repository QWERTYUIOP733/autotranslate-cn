package com.autotranslate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 在线翻译兜底：
 * 词典未覆盖的英文文本，异步调用可配置的翻译服务（默认 Google 免费接口，无需 API Key），
 * 翻译结果：
 *  1. 立即合并进运行时词典（byText：英文原文 -> 中文）
 *  2. 持久化到 config/autotranslate/dict/cache.json，下次启动自动加载
 *
 * 安全与限流：
 *  - 只翻译"像英文句子"的文本（含空格且基本为 ASCII 字母），跳过占位符/数字/中文
 *  - 相同文本去重（正在翻译的集合），每请求间隔 150ms 限流
 *  - 网络失败静默降级（保持英文，不影响游戏）
 */
public final class OnlineTranslator {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoTranslateMod.MOD_ID);
    /** 固定 2 线程：避免缺失键风暴时无限并发；翻译本身有全局限流 */
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "autotranslate-online");
        t.setDaemon(true);
        return t;
    });
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    /** 正在翻译/已翻译的去重集合 */
    private static final Set<String> IN_FLIGHT = new HashSet<>();
    private static final AtomicLong LAST_REQUEST = new AtomicLong(0);
    private static final long MIN_INTERVAL_MS = 150;
    /** 连续失败计数（用于告警） */
    private static final AtomicLong FAIL_STREAK = new AtomicLong(0);
    private static volatile boolean serviceDownWarned = false;

    private OnlineTranslator() {
    }

    /** 是否值得尝试翻译：包含英文字母、长度 3~400、无占位符/格式码/危险字符 */
    public static boolean isTranslatable(String text) {
        if (text == null || text.length() < 3 || text.length() > 400) {
            return false;
        }
        if (text.contains("%") || text.contains("§") || text.contains("\u00a7")) {
            return false; // 含占位符/格式码，翻译会破坏参数位置
        }
        boolean hasAscii = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') {
                hasAscii = true;
            }
            if (c > 0x2FFF) {
                return false; // 已含 CJK 等非拉丁字符，视为已翻译
            }
        }
        return hasAscii;
    }

    /** 启动时探测翻译服务可用性（异步，结果只记日志，不污染词典） */
    public static void probeService() {
        POOL.execute(() -> {
            try {
                String zh = translate("Sword");
                if (zh != null && !zh.isEmpty()) {
                    FAIL_STREAK.set(0);
                    serviceDownWarned = false;
                    LOGGER.info("[自动汉化] 翻译服务在线：探测词 \"Sword\" -> \"{}\"", zh);
                } else {
                    LOGGER.warn("[自动汉化] 翻译服务无返回结果（服务可能异常），在线兜底将保持英文");
                }
            } catch (Exception e) {
                LOGGER.warn("[自动汉化] 翻译服务不可用（{}），在线兜底将保持英文；可检查 translateServiceUrl 与本地服务是否启动",
                        e.toString());
            }
        });
    }

    /** 异步请求翻译（幂等去重）。结果回调合并进词典并持久化。 */
    public static void request(String englishText) {
        if (!ModConfig.isOnlineTranslate() || !isTranslatable(englishText)) {
            return;
        }
        synchronized (IN_FLIGHT) {
            if (IN_FLIGHT.contains(englishText)) {
                return;
            }
            IN_FLIGHT.add(englishText);
        }
        POOL.execute(() -> {
            try {
                // 限流：与上一次请求保持间隔
                long wait = MIN_INTERVAL_MS - (System.currentTimeMillis() - LAST_REQUEST.get());
                if (wait > 0) {
                    try {
                        Thread.sleep(wait);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
                LAST_REQUEST.set(System.currentTimeMillis());

                String zh = translate(englishText);
                if (zh != null && !zh.isEmpty() && !zh.equals(englishText)) {
                    FAIL_STREAK.set(0);
                    Dictionary.addTextEntry(englishText, zh);
                    LOGGER.debug("[自动汉化] 在线翻译：\"{}\" -> \"{}\"", englishText, zh);
                } else {
                    LOGGER.debug("[自动汉化] 在线翻译无结果或结果不变: {}", englishText);
                }
            } catch (Exception e) {
                long streak = FAIL_STREAK.incrementAndGet();
                if (streak >= 5 && !serviceDownWarned) {
                    serviceDownWarned = true;
                    LOGGER.warn("[自动汉化] 翻译服务连续 {} 次失败，已自动降级为英文（不中断游戏）。" +
                            "请检查本地翻译服务/网络后重启，或关闭在线翻译。", streak);
                }
                LOGGER.debug("[自动汉化] 在线翻译失败（保持英文）: {} - {}", englishText, e.toString());
            } finally {
                synchronized (IN_FLIGHT) {
                    IN_FLIGHT.remove(englishText);
                }
            }
        });
    }

    /** 调用翻译服务，解析出中文译文；失败返回 null（调用方静默降级）。瞬时失败自动重试 1 次。 */
    private static String translate(String englishText) throws IOException, InterruptedException {
        String zh = doTranslate(englishText);
        if (zh != null) {
            return zh;
        }
        // 瞬时失败（网络抖动/服务重启中）：2 秒后重试一次
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return doTranslate(englishText);
    }

    private static String doTranslate(String englishText) throws IOException, InterruptedException {
        String url = ModConfig.getTranslateServiceUrl().replace("%s",
                URLEncoder.encode(englishText, StandardCharsets.UTF_8));
        if (!url.contains("q=") && !url.contains("text=")) {
            LOGGER.warn("[自动汉化] 翻译服务地址模板缺少 %s 参数位: {}", url);
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0 autotranslate/1.6")
                .GET()
                .build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            LOGGER.debug("[自动汉化] 翻译服务返回 {}: {}", resp.statusCode(), url);
            return null;
        }
        // 按配置引擎或响应结构自动识别协议
        String engine = ModConfig.getTranslateEngine();
        if ("google".equals(engine)) {
            return parseGtx(resp.body());
        }
        if ("argos".equals(engine)) {
            return parseArgos(resp.body());
        }
        // auto：Argos 响应含 translatedText 字段；Google 响应是嵌套数组
        String body = resp.body();
        if (body.contains("\"translatedText\"")) {
            return parseArgos(body);
        }
        return parseGtx(body);
    }

    /** 解析 Argos Translate HTTP 服务返回：{"translatedText":"中文", ...} */
    private static String parseArgos(String body) {
        try {
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            if (obj.has("translatedText") && obj.get("translatedText").isJsonPrimitive()) {
                String t = obj.get("translatedText").getAsString();
                return t.isEmpty() ? null : t;
            }
        } catch (Exception e) {
            LOGGER.debug("[自动汉化] Argos 翻译响应解析失败: {}", body);
        }
        return null;
    }

    /** 解析 Google gtx 接口返回：[[["译文","原文",null,null,10]],null,"en",...] */
    private static String parseGtx(String body) {
        try {
            JsonArray root = JsonParser.parseString(body).getAsJsonArray();
            JsonArray sentences = root.get(0).getAsJsonArray();
            StringBuilder sb = new StringBuilder();
            for (JsonElement s : sentences) {
                JsonElement t = s.getAsJsonArray().get(0);
                if (t != null && t.isJsonPrimitive()) {
                    sb.append(t.getAsString());
                }
            }
            return sb.toString();
        } catch (Exception e) {
            LOGGER.debug("[自动汉化] 翻译响应解析失败: {}", body);
            return null;
        }
    }
}
