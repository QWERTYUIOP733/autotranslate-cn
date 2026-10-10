package com.autotranslate.mixin;

import com.autotranslate.Dictionary;
import com.autotranslate.MissingKeysRecorder;
import com.autotranslate.ModConfig;
import com.autotranslate.OnlineTranslator;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截 Minecraft 26.3 的统一翻译入口 Language#getOrDefault(String)。
 * 原版与几乎所有模组的 translatable 文本最终都经过此方法。
 *
 * - HEAD 注入：overrideExisting=true（词典优先）时按翻译键直接替换
 * - RETURN 注入：仅补缺模式下，原版无翻译（返回 key 本身）时按翻译键补缺；
 *                并支持按英文原文匹配（byText）；仍未覆盖时记录缺失键并触发在线翻译兜底
 */
@Mixin(Language.class)
public abstract class LanguageMixin {

    @Inject(method = "getOrDefault(Ljava/lang/String;)Ljava/lang/String;",
            at = @At("HEAD"), cancellable = true)
    private void autotranslate$head(String key, CallbackInfoReturnable<String> cir) {
        if (!ModConfig.isEnabled() || !ModConfig.isOverrideExisting()) {
            return;
        }
        try {
            String zh = Dictionary.lookupKey(key);
            if (zh != null) {
                cir.setReturnValue(zh);
            }
        } catch (Exception e) {
            // 注入点绝不允许影响游戏：任何意外异常都放行原逻辑
        }
    }

    @Inject(method = "getOrDefault(Ljava/lang/String;)Ljava/lang/String;",
            at = @At("RETURN"), cancellable = true)
    private void autotranslate$return(String key, CallbackInfoReturnable<String> cir) {
        if (!ModConfig.isEnabled()) {
            return;
        }
        try {
            String original = cir.getReturnValue();
            if (original == null || original.isEmpty()) {
                return;
            }
            // 补缺：原版没有这条翻译（返回的是 key 本身）
            if (original.equals(key)) {
                String zh = Dictionary.lookupKey(key);
                if (zh != null) {
                    cir.setReturnValue(zh);
                    return;
                }
                // 仍未覆盖：记录缺失键（供用户反馈/自译），并在开启在线翻译时尝试翻译
                MissingKeysRecorder.record(key, key);
                OnlineTranslator.request(key);
                return;
            }
            // 按英文原文匹配（跳过 key 本身，避免死循环/无意义替换）
            if (!original.equals(key)) {
                String zh = Dictionary.lookupText(original);
                if (zh != null) {
                    cir.setReturnValue(zh);
                    return;
                }
                // 英文原文仍未覆盖：记录 + 在线翻译兜底
                MissingKeysRecorder.record(key, original);
                OnlineTranslator.request(original);
            }
        } catch (Exception e) {
            // 注入点绝不允许影响游戏：任何意外异常都放行原逻辑
        }
    }
}
