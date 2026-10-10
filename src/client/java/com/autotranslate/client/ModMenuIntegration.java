package com.autotranslate.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu 集成入口。
 * 在 Mod Menu 模组列表中点击"配置"打开 AutoTranslateConfigScreen。
 * 仅在安装了 Mod Menu 时被加载；未安装不影响模组其他功能。
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return AutoTranslateConfigScreen::new;
    }
}
