package com.autotranslate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 硬编码文本翻译器（深度汉化）：
 * 拦截字体渲染层绘制的 String 文本，把"不走翻译系统的硬编码英文"（如
 * Xaero 的 "Ignore Enforced As Editor"）也翻译成中文 —— 最后一块死角。
 *
 * 过滤规则（避免误翻聊天、玩家名、告示牌等世界内容）：
 *  - 只处理 2~64 字符、含 ASCII 字母、无格式码/占位符/换行的短文本
 *  - 已含中文（已翻译）的跳过
 *  - 档位1（词典覆盖）：只翻词典已收录的英文原文，未收录仅记录
 *  - 档位2（全自动）：未收录的短英文文本额外触发在线翻译（有轻微误翻风险，谨慎使用）
 *
 * 注意：在线翻译的结果会沉淀进词典并持久化，下一次绘制同一文本即生效。
 */
public final class HardcodedTextTranslator {
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoTranslateMod.MOD_ID);

    private HardcodedTextTranslator() {
    }

    /** 渲染层入口：返回译文；不满足条件或未命中时返回 null（保持原文本） */
    public static String translate(String text) {
        if (!ModConfig.isDeepTranslateOn() || text == null) {
            return null;
        }
        if (!isUiText(text)) {
            return null;
        }        // 1. 词典命中 → 直接替换
        String zh = Dictionary.lookupKey(text);
        if (zh == null) {
            zh = Dictionary.lookupText(text);
        }
        if (zh != null) {
            return zh;
        }
        // 2. 未命中
        MissingKeysRecorder.recordHardcoded(text);
        if (ModConfig.isDeepTranslateAuto() && ModConfig.isOnlineTranslate()) {
            // 全自动档位：异步在线翻译，翻译完成后同一文本自动生效
            OnlineTranslator.request(text);
        }
        return null;
    }

    /** 判断是否值得翻译的 UI 短文本（防止误翻玩家/世界内容）。公开供渲染层各通道复用。 */
    public static boolean isUiText(String text) {
        int len = text.length();
        if (len < 2 || len > 64) {
            return false;
        }
        if (text.indexOf('\n') >= 0 || text.indexOf('\u00a7') >= 0
                || text.indexOf('%') >= 0 || text.indexOf('\\') >= 0) {
            return false;
        }
        boolean hasAscii = false;
        int words = 1;
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') {
                hasAscii = true;
            } else if (c > 0x2FFF) {
                return false; // 已含 CJK 等非拉丁字符，视为已翻译
            } else if (c == ' ') {
                words++;
            }
        }
        if (!hasAscii) {
            return false;
        }
        // 全自动档位对更长/更多词的文本更谨慎（聊天消息通常是长句）
        if (words > 12) {
            return false;
        }
        if (!ModConfig.isDeepTranslateAuto() && words > 8) {
            return false;
        }
        return true;
    }
}
