# Auto Translate CN 自动汉化模组

适配 **Minecraft Java 26.3（最新正式版）+ Fabric** 的客户端自动汉化模组。
**自动扫描 mods 文件夹中所有其他模组并汉化其界面文本**，完全离线、零配置。拦截游戏统一翻译入口 `Language#getOrDefault`，把未汉化的文本离线替换为简体中文。

## 功能

- **【核心】自动扫描汉化全部模组**：启动时自动检测 mods 目录下所有其他模组，扫描其界面文本并汉化
  - 内置 **CFPA 模组翻译包**（覆盖 1600+ 模组，含 Xaero、JEI、钠等主流模组）
  - 优先使用模组自带中文（自带 zh_cn.json 时优先于 CFPA）
  - 自动生成英文原文匹配（byText），直接写死英文的文本也能汉化
- 拦截原版与所有模组的 translatable 文本，按词典自动汉化
- 内置完整翻译词典（`assets/autotranslate/lang/zh_cn_dict.json`）：byKey 8,578 条 + byText 7,655 条，覆盖 Minecraft 26.3 官方全部翻译键
- 支持外部词典扩展：把任意 `.json` 词典放入 `config/autotranslate/dict/` 即自动加载（优先级最高）
- **Mod Menu 配置界面**（推荐安装 Mod Menu）：模组菜单中点击「配置」可视化设置总开关、覆盖策略、一键重载词典
- 词典热重载：配置界面一键重载（会重新扫描 mods），或游戏内执行 `/autotranslate reload`

## 安装

1. 安装 Fabric Loader 0.19.5+（26.3 版本），并将游戏语言设为简体中文
2. 安装 Fabric API（0.161.0+26.3 或更高，本模组依赖）
3. 将 `autotranslate-1.3.0.jar` 放入 `mods` 文件夹
4. 启动游戏即可生效，模组会自动扫描并汉化同文件夹的其他模组
5. （可选）安装 Mod Menu 模组，可在游戏内「模组列表 → 自动汉化 → 配置」打开设置界面

> 未安装 Mod Menu 时不影响汉化功能，开关与策略仍可通过 `/autotranslate` 命令与 `config/autotranslate/config.json` 调整。

## 模组汉化数据来源与许可

- **CFPA 模组翻译包**（[CFPAOrg/Minecraft-Mod-Language-Package](https://github.com/CFPAOrg/Minecraft-Mod-Language-Package)），许可证 **CC BY-NC-SA 4.0**（见 `LICENSE_CFPA`），仅限非商业用途
- 各模组自带的中文语言文件（如 JEI、苹果皮自带 `zh_cn.json`）优先于 CFPA 数据

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

`config/autotranslate/config.json`（也可在游戏内 Mod Menu 配置界面修改，效果相同）：

| 字段 | 默认 | 说明 |
|---|---|---|
| `enabled` | `true` | 总开关 |
| `overrideExisting` | `false` | `true`=词典优先（覆盖已有翻译）；`false`=仅补缺（原版无翻译才用词典） |
| `externalDictDir` | `autotranslate/dict` | 外部词典目录（相对 config 目录） |

## 构建

```bash
# 一键构建（自动设置 JDK 25 与 Gradle 缓存，产物在 D 盘）
build.bat
# 产物：build/libs/autotranslate-1.3.0.jar
```

## 技术要点

- 版本锁定：Minecraft 26.3 / Fabric Loader 0.19.5 / Fabric API 0.162.0+26.3 / Loom 1.18.3
- 26.1 起游戏不再混淆，直接使用 Mojang 官方类名（如 `net.minecraft.locale.Language`）
- Mixin 双注入点：`@Inject(HEAD)` 实现"词典优先"，`@Inject(RETURN)` 实现"仅补缺 + 英文原文匹配"
- 代码结构：入口（命令注册）/ 客户端初始化 / 词典加载器 / 配置 / Mixin 拦截器
