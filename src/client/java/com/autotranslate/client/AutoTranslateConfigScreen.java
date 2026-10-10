package com.autotranslate.client;

import com.autotranslate.Dictionary;
import com.autotranslate.ModConfig;
import com.autotranslate.ModScanner;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 自动汉化配置界面（Mod Menu 配置屏）。
 * 基于 Cloth Config API 构建：自带滚动、分类、下拉选择、撤销/重置/保存按钮，
 * 任意窗口尺寸下都能完整查看全部配置项（解决此前自绘界面在低高度窗口下按钮超出屏幕的问题）。
 *
 * 承载配置项：
 * - 总开关 enabled
 * - 词典优先 overrideExisting
 * - 本地 AI 翻译 onlineTranslate
 * - 翻译引擎 translateEngine（argos / google / auto）
 * - 深度汉化 deepTranslate（关 / 词典覆盖 / 全自动）
 * - 导出缺失键 exportMissing
 * - 翻译服务地址 translateServiceUrl
 * - 当前扫描/词典状态说明（只读）
 */
public final class AutoTranslateConfigScreen {

    private AutoTranslateConfigScreen() {
    }

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("自动汉化设置"));
        ConfigEntryBuilder entry = builder.entryBuilder();
        ConfigCategory main = builder.getOrCreateCategory(Component.literal("翻译设置"));

        // 总开关
        main.addEntry(entry.startBooleanToggle(Component.literal("总开关"), ModConfig.isEnabled())
                .setDefaultValue(true)
                .setSaveConsumer(ModConfig::setEnabled)
                .setTooltip(Component.literal("关闭后完全放行原版逻辑，不做任何翻译"))
                .build());

        // 词典优先
        main.addEntry(entry.startBooleanToggle(Component.literal("词典优先"), ModConfig.isOverrideExisting())
                .setDefaultValue(true)
                .setSaveConsumer(ModConfig::setOverrideExisting)
                .setTooltip(Component.literal("开 = 词典命中即覆盖原版/其他模组翻译；关 = 仅补缺（原版没有翻译时才用词典）"))
                .build());

        // 本地 AI 翻译
        main.addEntry(entry.startBooleanToggle(Component.literal("本地 AI 翻译"), ModConfig.isOnlineTranslate())
                .setDefaultValue(true)
                .setSaveConsumer(ModConfig::setOnlineTranslate)
                .setTooltip(Component.literal("词典未收录的英文文本自动交给本地/远程 AI 翻译，译文回填词典并持久化；聊天/世界内容自动过滤不会误翻"))
                .build());

        // 翻译引擎（argos 本地 / google 在线 / auto 自动识别）
        main.addEntry(entry.startStringDropdownMenu(Component.literal("翻译引擎"), ModConfig.getTranslateEngine())
                .setDefaultValue("argos")
                .setSelections(java.util.List.of("argos", "google", "auto"))
                .setSaveConsumer(ModConfig::setTranslateEngine)
                .setTooltip(Component.literal("argos = 本地 Argos（默认，完全离线）；google = Google 免费接口；auto = 按响应自动识别"))
                .build());

        // 深度汉化档位（字符串下拉映射 int）
        String deepNow = ModConfig.getDeepTranslate() == 0 ? "关"
                : ModConfig.getDeepTranslate() == 1 ? "词典覆盖" : "全自动";
        main.addEntry(entry.startStringDropdownMenu(Component.literal("深度汉化"), deepNow)
                .setDefaultValue("全自动")
                .setSelections(java.util.List.of("关", "词典覆盖", "全自动"))
                .setSaveConsumer(v -> ModConfig.setDeepTranslate(
                        "全自动".equals(v) ? 2 : "词典覆盖".equals(v) ? 1 : 0))
                .setTooltip(Component.literal("拦截渲染层硬编码英文（配置菜单等绕过翻译系统的文本）。关 = 不拦截；词典覆盖 = 只翻已收录；全自动 = 未收录短文本也交本地 AI 翻译"))
                .build());

        // 导出缺失键
        main.addEntry(entry.startBooleanToggle(Component.literal("导出缺失键"), ModConfig.isExportMissing())
                .setDefaultValue(true)
                .setSaveConsumer(ModConfig::setExportMissing)
                .setTooltip(Component.literal("把运行时未翻译的文本记录到 config/autotranslate/dict/missing_*.txt，用于反馈与词典补全"))
                .build());

        // 翻译服务地址
        main.addEntry(entry.startTextField(Component.literal("翻译服务地址"), ModConfig.getTranslateServiceUrl())
                .setDefaultValue("http://127.0.0.1:5000/translate?q=%s&source=en&target=zh")
                .setSaveConsumer(ModConfig::setTranslateServiceUrl)
                .setTooltip(Component.literal("支持分号分隔多引擎（自动 failover），地址需包含 %s 参数位"))
                .build());

        // 状态说明（只读）：本次扫描到的模组与词典规模
        String info = "本次扫描模组 " + ModScanner.getScannedModIds().size() + " 个"
                + "；词典 " + Dictionary.size() + " 条（byKey + byText）"
                + "；本地 AI 服务地址：127.0.0.1:5000";
        main.addEntry(entry.startTextDescription(Component.literal(info)).build());

        builder.setSavingRunnable(ModConfig::save);
        return builder.build();
    }
}
