# Auto Translate CN 自动汉化模组

适配 **Minecraft Java 26.3（最新正式版）+ Fabric** 的客户端自动汉化模组。
采用**内置翻译字典**方案：拦截游戏统一翻译入口 `Language#getOrDefault`，把未汉化的文本离线替换为简体中文。完全离线、零配置启动、不影响服务端与其他模组。

## 功能

- 拦截原版与所有模组的 translatable 文本，按词典自动汉化
- 内置完整翻译词典（`assets/autotranslate/lang/zh_cn_dict.json`）：byKey 8,578 条 + byText 7,655 条，覆盖 Minecraft 26.3 官方全部翻译键
- 支持外部词典扩展：把任意 `.json` 词典放入 `config/autotranslate/dict/` 即自动加载
- 词典热重载：游戏内执行 `/autotranslate reload`
- 状态查看：`/autotranslate status`
- 可配置：总开关、覆盖策略（词典优先 / 仅补缺）、外部词典目录

## 安装

1. 安装 Fabric Loader 0.19.5+（26.3 版本），并将游戏语言设为简体中文
2. 安装 Fabric API（0.161.0+26.3 或更高，本模组依赖）
3. 将 `autotranslate-1.1.0.jar` 放入 `mods` 文件夹
4. 启动游戏即可生效

## 外部词典格式

支持两种 JSON 格式（UTF-8）：

**结构式（推荐）**
```json
{
  "byKey":  { "item.minecraft.diamond": "钻石" },
  "byText": { "Diamond Sword": "钻石剑" }
}
```

**扁平式**（无 byKey/byText 时整体按翻译键处理）
```json
{
  "item.minecraft.diamond": "钻石"
}
```

> 提示：CFPA「Minecraft Mod Language Package」的 `zh_cn.json` 可直接放入外部词典目录使用（注意遵守其开源许可）。

## 配置文件

`config/autotranslate/config.json`：

| 字段 | 默认 | 说明 |
|---|---|---|
| `enabled` | `true` | 总开关 |
| `overrideExisting` | `false` | `true`=词典优先（覆盖已有翻译）；`false`=仅补缺（原版无翻译才用词典） |
| `externalDictDir` | `autotranslate/dict` | 外部词典目录（相对 config 目录） |

## 构建

```bash
# 一键构建（自动设置 JDK 25 与 Gradle 缓存，产物在 D 盘）
build.bat
# 产物：build/libs/autotranslate-1.1.0.jar
```

## 技术要点

- 版本锁定：Minecraft 26.3 / Fabric Loader 0.19.5 / Fabric API 0.162.0+26.3 / Loom 1.18.3
- 26.1 起游戏不再混淆，直接使用 Mojang 官方类名（如 `net.minecraft.locale.Language`）
- Mixin 双注入点：`@Inject(HEAD)` 实现"词典优先"，`@Inject(RETURN)` 实现"仅补缺 + 英文原文匹配"
- 代码结构：入口（命令注册）/ 客户端初始化 / 词典加载器 / 配置 / Mixin 拦截器
