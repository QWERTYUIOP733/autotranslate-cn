package com.autotranslate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
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
                    Dictionary.addTextEntry(englishText, zh);
                    LOGGER.info("[自动汉化] 在线翻译：\"{}\" -> \"{}\"", englishText, zh);
                } else {
                    LOGGER.debug("[自动汉化] 在线翻译无结果或结果不变: {}", englishText);
                }
            } catch (Exception e) {
                LOGGER.debug("[自动汉化] 在线翻译失败（保持英文）: {} - {}", englishText, e.toString());
            } finally {
                synchronized (IN_FLIGHT) {
                    IN_FLIGHT.remove(englishText);
                }
            }
        });
    }

    /** 调用翻译服务，解析出中文译文；失败返回 null（调用方静默降级） */
    private static String translate(String englishText) throws IOException, InterruptedException {
        String url = ModConfig.getTranslateServiceUrl().replace("%s",
                URLEncoder.encode(englishText, StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0 autotranslate/1.4")
                .GET()
                .build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            LOGGER.debug("[自动汉化] 翻译服务返回 {}: {}", resp.statusCode(), url);
            return null;
        }
        return parseGtx(resp.body());
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
