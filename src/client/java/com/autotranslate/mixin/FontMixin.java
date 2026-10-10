package com.autotranslate.mixin;

import com.autotranslate.HardcodedTextTranslator;
import com.autotranslate.ModConfig;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 深度汉化：拦截字体渲染层的 String 绘制入口。
 *
 * 26.3 渲染管线重构后，所有"直接绘制字符串"（模组硬编码文本、drawString 等）
 * 最终都经过 Font#prepareText(String, ...)。此注入在方法入口修改 String 参数：
 * 词典命中的英文原文 → 替换为中文；未命中的按档位记录/在线翻译（异步，下帧生效）。
 *
 * 聊天、玩家名等走 FormattedCharSequence 版本的 prepareText，不受影响。
 * 开启条件：配置「深度汉化」（默认关），档位见 ModConfig。
 */
@Mixin(Font.class)
public abstract class FontMixin {

    @ModifyVariable(method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String autotranslate$deepTranslate(String text) {
        if (!ModConfig.isDeepTranslateOn()) {
            return text;
        }
        String zh = HardcodedTextTranslator.translate(text);
        return zh != null ? zh : text;
    }
}
