# Auto Translate CN 自动汉化模组

适配 **Minecraft Java 26.3（最新正式版）+ Fabric** 的客户端自动汉化模组。
**自动扫描 mods 文件夹中所有其他模组并汉化其界面文本**，无需逐个装汉化包。
从翻译键到渲染层六级覆盖，并支持接入**本地部署的 AI 翻译引擎**（完全离线）。

## 功能

- **【核心】自动扫描汉化全部模组**：启动时自动检测 mods 目录下所有其他模组，扫描其界面文本并汉化
  - 内置 **CFPA 模组翻译包**（覆盖 1600+ 模组，含 Xaero、JEI、钠等主流模组）
  - 优先使用模组自带中文（自带 zh_cn.json 时优先于 CFPA），**全命名空间扫描**——兼容 cloth-config（命名空间 cloth-config2）等一切命名空间不一致的情况，配置库自带中文自动加载
  - 自动生成英文原文匹配（byText），直接写死英文的文本也能汉化
- 拦截原版与所有模组的 translatable 文本，按词典自动汉化
- 内置完整翻译词典（`assets/autotranslate/lang/zh_cn_dict.json`）：byKey 8,578 条 + byText 7,655 条，覆盖 Minecraft 26.3 官方全部翻译键
- 支持外部词典扩展：把任意 `.json` / `.lang` 词典放入 `config/autotranslate/dict/` 即自动加载（优先级最高）
- **深度汉化**：渲染层拦截不走翻译系统的硬编码英文（如 Xaero 设置页 "Ignore Enforced As Editor"），三档开关：关 / 词典覆盖 / 全自动
- **在线翻译兜底**：词典未覆盖的文本自动联网翻译（异步不卡游戏），结果缓存到本地，下次启动自动加载；支持 Google 在线 / 本地 Argos 双引擎自动识别
- **缺失键自动导出**：未翻译文本自动记录到 `config/autotranslate/dict/missing_zh_cn.txt`（.lang 格式）与 `missing_hardcoded.txt`，改名放入词典目录即自制翻译，或反馈给开发者内置
- **Mod Menu 配置界面**（推荐安装 Mod Menu）：模组菜单中点击「配置」可视化设置总开关、覆盖策略、翻译引擎、深度汉化、在线翻译、一键重载词典

## 安装

1. 安装 Fabric Loader 0.19.5+（26.3 版本），并将游戏语言设为简体中文
2. 安装 Fabric API（0.161.0+26.3 或更高，本模组依赖）
3. 将 `autotranslate-1.5.0.jar` 放入 `mods` 文件夹
4. 启动游戏即可生效，模组会自动扫描并汉化同文件夹的其他模组
5. （可选）安装 Mod Menu 模组，可在游戏内「模组列表 → 自动汉化 → 配置」打开设置界面

> 未安装 Mod Menu 时不影响汉化功能，开关与策略仍可通过 `config/autotranslate/config.json` 调整。

## 本地 AI 翻译（离线，可选）

想 100% 离线汉化新模组？接入 **Argos Translate** 本地引擎（纯 CPU 推理，**无需 GPU/显存**，模型约 200MB）：

1. 双击运行 `本地翻译服务/install.bat`（自动装 Python 依赖 + 下载 en→zh 模型，仅一次）
2. 双击运行 `本地翻译服务/start.bat`（在 127.0.0.1:5000 启动本地翻译服务）
3. 游戏内 Mod Menu → 自动汉化 → 配置：把「翻译引擎」设为「本地 Argos」，开启「在线翻译」与「深度汉化=全自动」

之后所有词典未覆盖的英文文本都会经本地 AI 翻译，结果缓存持久化，完全离线、免费、隐私不出本机。

> 没有本地引擎时，模组默认使用 Google 免费在线接口（「翻译引擎=自动识别」可自动兼容两种协议）。

## 授权与版权（重要）

本项目采用**三层授权**，数据与代码分离：

| 内容 | 许可证/版权 | 说明 |
|---|---|---|
| 模组代码（Java/Mixin/配置） | **MIT**（`LICENSE` / `LICENSE_autotranslate`） | 可自由使用、修改、分发（需保留版权声明） |
| 内置 CFPA 模组翻译包（`cfpa_mods.zip`） | **CC BY-NC-SA 4.0**（`LICENSE_CFPA`，随 jar 分发） | 数据来源 [CFPAOrg/Minecraft-Mod-Language-Package](https://github.com/CFPAOrg/Minecraft-Mod-Language-Package)；**仅限非商业用途**，修改/再分发须相同方式共享并署名 |
| 内置官方翻译词典（`zh_cn_dict.json`） | 版权归 **Mojang Studios / Microsoft** | 提取自 Minecraft 26.3 官方语言文件，仅作本地化使用，与代码分离 |
| 补充翻译（`extra_zh_cn.json`，661 条） | 本项目原创（MIT） | 针对 CFPA 缺失键的手工翻译，保留原版术语风格 |

- 内置的补充翻译与在线/本地翻译缓存结果均为本项目原创，不触发 CC BY-NC-SA 的相同方式共享义务
- 本地 AI 翻译为可选外部工具：**Argos Translate**（MIT，第三方，未打包进 jar，运行时由用户自行安装）

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

> 提示：CFPA「Minecraft Mod Language Package」的 `zh_cn.json` 可直接放入外部词典目录使用（注意遵守其开源许可 CC BY-NC-SA 4.0）。

## 配置文件

`config/autotranslate/config.json`（也可在游戏内 Mod Menu 配置界面修改，效果相同）：

| 字段 | 默认 | 说明 |
|---|---|---|
| `enabled` | `true` | 总开关 |
| `overrideExisting` | `false` | `true`=词典优先（覆盖已有翻译）；`false`=仅补缺（原版无翻译才用词典） |
| `externalDictDir` | `autotranslate/dict` | 外部词典目录（相对 config 目录） |
| `onlineTranslate` | `false` | 在线/本地翻译兜底开关 |
| `translateEngine` | `auto` | `auto`=自动识别 / `google`=在线 / `argos`=本地 Argos 服务 |
| `translateServiceUrl` | Google 接口 | 翻译服务地址模板（`%s` 为 URL 编码的英文文本） |
| `exportMissing` | `true` | 导出未覆盖翻译键到 missing_zh_cn.txt |
| `deepTranslate` | `0` | 深度汉化：`0`=关 / `1`=词典覆盖 / `2`=全自动 |

## 构建

```bash
# 一键构建（自动设置 JDK 25 与 Gradle 缓存）
build.bat
# 产物：build/libs/autotranslate-1.5.0.jar
```

## 技术要点

- 版本锁定：Minecraft 26.3 / Fabric Loader 0.19.5 / Fabric API 0.162.0+26.3 / Loom 1.18.3
- 26.1 起游戏不再混淆，直接使用 Mojang 官方类名（如 `net.minecraft.locale.Language`）
- Mixin 双注入点：`@Inject(HEAD)` 实现"词典优先"，`@Inject(RETURN)` 实现"仅补缺 + 英文原文匹配 + 缺失记录"
- `FontMixin`（客户端）拦截 `Font#prepareText(String)` 渲染入口，实现硬编码文本的深度汉化；聊天/玩家名走 FormattedCharSequence 通道不受影响
- 在线翻译异步执行（独立线程池 + 限流 + 去重 + 失败静默降级），结果合并内存词典并持久化 `cache.json`
- 代码结构：入口 / 客户端初始化 / 词典加载器 / 模组扫描器 / 在线翻译器 / 硬编码翻译器 / 缺失记录器 / 配置 / Mixin 拦截器
