package com.autotranslate.client;

import com.autotranslate.AutoTranslateMod;
import com.autotranslate.Dictionary;
import com.autotranslate.ModConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class AutoTranslateModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 启动早期加载配置与词典，确保首次翻译查询即生效
        ModConfig.load();
        Dictionary.reload();
        AutoTranslateMod.LOGGER.info("[自动汉化] 客户端初始化完成，词典共 {} 条", Dictionary.size());
    }
}
