package com.autotranslate.mixin;

import com.autotranslate.Dictionary;
import com.autotranslate.HardcodedTextTranslator;
import com.autotranslate.MissingKeysRecorder;
import com.autotranslate.ModConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 深度汉化：拦截字体渲染层的两处绘制入口。
 *
 * 26.3 渲染管线重构后，文本绘制最终都经过 Font 的两个 prepareText 重载：
 * 1) prepareText(String, ...)     —— 模组硬编码 drawString 的纯字符串文本
 * 2) prepareText(FormattedCharSequence, ...) —— Component（translatable/literal）渲染
 *
 * 两个入口都做词典命中替换；区别：
 * - String 入口：完整走 HardcodedTextTranslator（词典命中 + 特征过滤 + 档位2在线兜底）
 * - FormattedCharSequence 入口：**仅做 byText 词典精确命中替换**，不做在线翻译请求，
 *   避免把玩家聊天、告示牌等世界内容也送去翻译（误翻风险）；新模组配置文本
 *   未命中时随 missing_hardcoded 导出，供反馈补词典。
 */
@Mixin(Font.class)
public abstract class FontMixin {

    @ModifyVariable(method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String autotranslate$deepTranslateString(String text) {
        if (!ModConfig.isDeepTranslateOn()) {
            return text;
        }
        String zh = HardcodedTextTranslator.translate(text);
        return zh != null ? zh : text;
    }

    @ModifyVariable(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private FormattedCharSequence autotranslate$deepTranslateFcs(FormattedCharSequence seq) {
        if (!ModConfig.isDeepTranslateOn() || seq == null) {
            return seq;
        }
        String text = fcsToString(seq);
        if (text == null || text.isEmpty()) {
            return seq;
        }
        // 仅词典精确命中才替换（byText），避免误翻聊天/世界内容
        String zh = Dictionary.lookupText(text);
        if (zh != null) {
            try {
                return Component.literal(zh).getVisualOrderText();
            } catch (Exception e) {
                return seq;
            }
        }
        // 未命中：若像是 UI 文本则记录（供词典补全闭环），聊天/世界内容不记录
        if (HardcodedTextTranslator.isUiText(text)) {
            MissingKeysRecorder.recordHardcoded(text);
        }
        return seq;
    }

    /** 把 FormattedCharSequence 还原为纯字符串（含样式文本） */
    private static String fcsToString(FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        boolean[] overflow = {false};
        seq.accept((index, style, codePoint) -> {
            if (sb.length() > 256) {
                overflow[0] = true;
                return false;
            }
            sb.appendCodePoint(codePoint);
            return true;
        });
        return overflow[0] ? null : sb.toString();
    }
}
