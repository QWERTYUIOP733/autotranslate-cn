package com.autotranslate;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoTranslateMod implements ModInitializer {
    public static final String MOD_ID = "autotranslate";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // /autotranslate reload —— 热重载词典
        // /autotranslate status —— 查看状态
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal(MOD_ID)
                    .then(Commands.literal("reload")
                            .executes(context -> {
                                Dictionary.reload();
                                context.getSource().sendSuccess(
                                        () -> Component.literal("[自动汉化] 词典已重载，当前 " + Dictionary.size() + " 条"),
                                        false);
                                return 1;
                            }))
                    .then(Commands.literal("status")
                            .executes(context -> {
                                context.getSource().sendSuccess(
                                        () -> Component.literal("[自动汉化] " + ModConfig.statusText() + "，词典 " + Dictionary.size() + " 条"),
                                        false);
                                return 1;
                            })));
        });

        // 启动时异步探测翻译服务可用性（结果仅记日志，便于诊断"在线兜底是否生效"）
        OnlineTranslator.probeService();

        LOGGER.info("[自动汉化] 模组初始化完成（Minecraft 26.3 / Fabric）");
    }
}
