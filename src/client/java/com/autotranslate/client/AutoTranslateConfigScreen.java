package com.autotranslate.client;

import com.autotranslate.AutoTranslateMod;
import com.autotranslate.Dictionary;
import com.autotranslate.ModConfig;
import com.autotranslate.ModScanner;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 自动汉化配置界面（Mod Menu 配置屏）。
 * 使用原版控件构建，不依赖 Cloth Config 等额外配置库。
 *
 * 承载功能：
 * - 总开关 enabled：开 / 关
 * - 覆盖策略 overrideExisting：仅补缺 / 词典优先
 * - 重载词典（替代 /autotranslate reload）
 * 修改即保存（写入 config/autotranslate/config.json），下次启动仍生效。
 */
public class AutoTranslateConfigScreen extends Screen {
    private static final int WIDTH = 220;
    private static final int HEIGHT = 20;
    private static final int SPACING = 28;

    private final Screen parent;

    public AutoTranslateConfigScreen(Screen parent) {
        super(Component.literal("自动汉化设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 130;

        // 总开关：开 / 关
        CycleButton<Boolean> enabledBtn = CycleButton.<Boolean>builder(
                        v -> Component.literal(v ? "开" : "关"),
                        () -> ModConfig.isEnabled())
                .withValues(false, true)
                .displayOnlyValue()
                .create(centerX - WIDTH / 2, y, WIDTH, HEIGHT,
                        Component.literal("总开关"),
                        (btn, val) -> {
                            ModConfig.setEnabled(val);
                            ModConfig.save();
                            AutoTranslateMod.LOGGER.info("[自动汉化] 配置界面：总开关已改为 {}", val ? "开" : "关");
                        });
        addRenderableWidget(enabledBtn);
        y += SPACING;

        // 覆盖策略：仅补缺 / 词典优先
        CycleButton<Boolean> overrideBtn = CycleButton.<Boolean>builder(
                        v -> Component.literal(v ? "词典优先" : "仅补缺"),
                        () -> ModConfig.isOverrideExisting())
                .withValues(false, true)
                .displayOnlyValue()
                .create(centerX - WIDTH / 2, y, WIDTH, HEIGHT,
                        Component.literal("覆盖策略"),
                        (btn, val) -> {
                            ModConfig.setOverrideExisting(val);
                            ModConfig.save();
                            AutoTranslateMod.LOGGER.info("[自动汉化] 配置界面：覆盖策略已改为 {}", val ? "词典优先" : "仅补缺");
                        });
        addRenderableWidget(overrideBtn);
        y += SPACING;

        // 在线翻译兜底：词典未覆盖的英文文本自动联网翻译（可配置服务地址）
        CycleButton<Boolean> onlineBtn = CycleButton.<Boolean>builder(
                        v -> Component.literal(v ? "开" : "关"),
                        () -> ModConfig.isOnlineTranslate())
                .withValues(false, true)
                .displayOnlyValue()
                .create(centerX - WIDTH / 2, y, WIDTH, HEIGHT,
                        Component.literal("在线翻译"),
                        (btn, val) -> {
                            ModConfig.setOnlineTranslate(val);
                            ModConfig.save();
                            AutoTranslateMod.LOGGER.info("[自动汉化] 配置界面：在线翻译已改为 {}", val ? "开" : "关");
                        });
        addRenderableWidget(onlineBtn);
        y += SPACING;

        // 翻译引擎：auto（自动识别）/ google（在线）/ argos（本地 Argos Translate 服务）
        CycleButton<String> engineBtn = CycleButton.<String>builder(
                        v -> Component.literal(
                                "auto".equals(v) ? "自动识别" : "google".equals(v) ? "在线 Google" : "本地 Argos"),
                        () -> ModConfig.getTranslateEngine())
                .withValues("auto", "google", "argos")
                .displayOnlyValue()
                .create(centerX - WIDTH / 2, y, WIDTH, HEIGHT,
                        Component.literal("翻译引擎"),
                        (btn, val) -> {
                            ModConfig.setTranslateEngine(val);
                            ModConfig.save();
                            AutoTranslateMod.LOGGER.info("[自动汉化] 配置界面：翻译引擎已改为 {}", val);
                        });
        addRenderableWidget(engineBtn);
        y += SPACING;

        // 深度汉化：渲染层拦截硬编码英文文本（不走翻译系统的），三档
        CycleButton<Integer> deepBtn = CycleButton.<Integer>builder(
                        v -> Component.literal(v == 0 ? "关" : v == 1 ? "词典覆盖" : "全自动"),
                        () -> ModConfig.getDeepTranslate())
                .withValues(0, 1, 2)
                .displayOnlyValue()
                .create(centerX - WIDTH / 2, y, WIDTH, HEIGHT,
                        Component.literal("深度汉化"),
                        (btn, val) -> {
                            ModConfig.setDeepTranslate(val);
                            ModConfig.save();
                            AutoTranslateMod.LOGGER.info("[自动汉化] 配置界面：深度汉化已改为 {}",
                                    val == 0 ? "关" : val == 1 ? "词典覆盖" : "全自动");
                        });
        addRenderableWidget(deepBtn);
        y += SPACING;

        // 导出缺失键：运行时遇到的未翻译文本记录到 missing_zh_cn.txt（便于反馈/自制翻译）
        CycleButton<Boolean> exportBtn = CycleButton.<Boolean>builder(
                        v -> Component.literal(v ? "开" : "关"),
                        () -> ModConfig.isExportMissing())
                .withValues(false, true)
                .displayOnlyValue()
                .create(centerX - WIDTH / 2, y, WIDTH, HEIGHT,
                        Component.literal("导出缺失键"),
                        (btn, val) -> {
                            ModConfig.setExportMissing(val);
                            ModConfig.save();
                            AutoTranslateMod.LOGGER.info("[自动汉化] 配置界面：导出缺失键已改为 {}", val ? "开" : "关");
                        });
        addRenderableWidget(exportBtn);
        y += SPACING;

        // 翻译服务地址（支持分号分隔多引擎，如 本地Argos;Google 免费接口；需含 %s 参数位）
        EditBox urlBox = new EditBox(this.font, centerX - WIDTH / 2, y, WIDTH, HEIGHT,
                Component.literal("翻译服务地址"));
        urlBox.setValue(ModConfig.getTranslateServiceUrl());
        urlBox.setMaxLength(512);
        addRenderableWidget(urlBox);
        y += SPACING;

        // 重载词典（替代命令 /autotranslate reload）
        Button reloadBtn = Button.builder(Component.literal("重载词典"), ignored -> {
                    Dictionary.reload();
                    AutoTranslateMod.LOGGER.info("[自动汉化] 配置界面：词典已重载，当前 {} 条", Dictionary.size());
                })
                .bounds(centerX - WIDTH / 2, y, WIDTH, HEIGHT)
                .build();
        addRenderableWidget(reloadBtn);
        y += SPACING;

        // 查看扫描报告：输出本次扫描到的全部模组 id 与词典规模到聊天栏
        Button scanBtn = Button.builder(Component.literal("查看扫描报告"), ignored -> {
                    var mods = ModScanner.getScannedModIds();
                    String msg = "已扫描模组 " + mods.size() + " 个：" + (mods.isEmpty() ? "无" : String.join(", ", mods))
                            + "；词典 " + Dictionary.size() + " 条（byKey + byText）";
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.sendSystemMessage(Component.literal("[自动汉化] " + msg));
                    }
                    AutoTranslateMod.LOGGER.info("[自动汉化] {}", msg);
                })
                .bounds(centerX - WIDTH / 2, y, WIDTH, HEIGHT)
                .build();
        addRenderableWidget(scanBtn);
        y += SPACING + 10;

        // 完成：保存服务地址后关闭
        Button doneBtn = Button.builder(Component.literal("完成"), ignored -> {
                    String v = urlBox.getValue();
                    if (v != null && !v.isBlank()) {
                        ModConfig.setTranslateServiceUrl(v.trim());
                        ModConfig.save();
                    }
                    this.onClose();
                })
                .bounds(centerX - WIDTH / 2, y, WIDTH, HEIGHT)
                .build();
        addRenderableWidget(doneBtn);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }
}
