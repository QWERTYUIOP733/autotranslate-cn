# HANDOFF — AutoTranslate CN 自动汉化模组（Minecraft 26.3 / Fabric）

> **文档用途**：本文件为跨 AI 会话交接文档，任何接手的 AI 应**首先完整阅读本文件**，再动代码。
> 所有路径均为绝对路径；所有命令均在 Windows PowerShell 5.1 环境验证过。
> 最后更新：2026-10-17 ｜ 当前版本：**v2.6.0**（已构建 / 已部署 / 已发布 Release）

---

## 1. 项目目标

- 为 **Minecraft Java 26.3（Fabric Loader 0.19.5）** 开发「自动汉化模组」：启动时**自动扫描 mods 文件夹内所有其他模组（除自身外全部），并将其文本翻译成简体中文**。
- 终极目标：**无论加载任何模组（物品/方块/小地图/配置菜单/库自渲染文本），都能自动适配，零人工干预**。
- 核心机制：**本地 AI 默认开启**——词典未收录的英文文本自动交给本地 Argos Translate 翻译，译文回填词典持久化。
- 交付约束：
  - 交付物为**直接可运行的 jar（不用压缩包）**；
  - 所有开发/工作文件一律放 **D 盘**（`D:\豆包工作区\` 分类目录），禁止写 C 盘（`C:\Users\...\Doubao\chats` 下的项目目录仅作对话工作区）；
  - 代码与发布走 GitHub 仓库 `https://github.com/QWERTYUIOP733/autotranslate-cn`。

---

## 2. 当前状态（截至 2026-10-17）

| 项 | 状态 |
|---|---|
| 最新版本 | **v2.6.0**（git HEAD `f5145c3`，tag v2.6.0 → `64d2164`） |
| 工程工作树 | 干净（git status clean） |
| 构建 | BUILD SUCCESSFUL（Loom 1.18.3 / JDK 25） |
| 发布 jar | `D:\豆包工作区\发布版本\autotranslate-2.6.0.jar`（7,021,052 字节 / 6856 KB） |
| 测试 mods 部署 | 已部署 2.6.0 单版本（SHA256 与发布目录一致） |
| GitHub Release | 已发布 v1.0.0 ~ v2.6.0 共 25 个版本 |
| 合规 | 三轮自查通过；2026-10-12 已重写全部 git 历史清除侵权材料（见 §11） |
| **唯一未完成主线** | **Task #44：三轮游戏内自检验收（等待用户游戏内截图反馈）** |

> **2026-10-17 归档清理记录**：开发过程中的临时核验目录、一次性脚本目录、构建中间产物已清理（共约 576 MB）：`D:\modtmp\`、`D:\豆包工作区\构建日志\`、工程 `build\`、工程 `.gradle\`。这些均为可再生的中间产物（重新构建/按需重写脚本即可恢复）。本文后续若出现这些路径，仅代表历史方法描述，需要时按同样方法**自建**目录（例如新建 ASCII 临时目录 `D:\modwork\`）。工程源码、本地翻译服务、独立词典仓库、发布 jar、工具链（JDK/Gradle 缓存）均完整保留。

---

## 3. 技术架构：三级翻译覆盖体系

游戏中所有可见英文文本只有三条来路，本模组在每条路都设了拦截点：

| 级别 | 通道 | 实现 | 覆盖对象 |
|---|---|---|---|
| ① 语言键 | `LanguageMixin`（`src/main/java/com/autotranslate/mixin/LanguageMixin.java`） | `@Inject` 拦截 `net.minecraft.locale.Language#getOrDefault(String)`：HEAD 注入实现 overrideExisting「词典优先」；RETURN 注入实现仅补缺 + byText 原文匹配 + 在线兜底 | 走标准翻译系统的模组文本（物品名、按键、菜单项、提示语） |
| ② 渲染层硬编码 | `FontMixin`（`src/client/java/com/autotranslate/client/mixin/FontMixin.java`） | 两个入口：Font 的 **String 入口** + **FormattedCharSequence（FCS）通道**；双通道 LRU 缓存上限 512 | Cloth Config / Configuration / PuzzlesLib 等库**绕过语言系统**直接把 en_us 值渲染成 Component 的文本（配置菜单等） |
| ③ AI 兜底 | `OnlineTranslator`（主源码） | 本地 Argos（默认 `http://127.0.0.1:5000/translate?q=%s&source=en&target=zh`）+ 在线 Google failover；服务地址分号分隔多引擎；深度汉化=全自动时 FCS 通道未命中的 UI 文本自动请求翻译 | 词典未收录文本；聊天/世界内容自动过滤（含 § 码、长句、已含中文一律不翻，防误翻） |

辅助组件：
- `ModScanner`：启动时扫描 mods 文件夹全部 jar，读取每个 fabric.mod.json，收集**全部命名空间**（修复过 cloth-config modid≠命名空间 cloth-config2 的问题）；提供 `getScannedModIds()`。
- `Dictionary`：词典加载与查询；byKey（语言键）+ byText（英文原文）两套索引；byText 大小写不敏感；`lookupText` 未命中时**剥离尾部 `...` 再匹配**（v2.4.2 根因修复，勿回退）。
- `HardcodedTextTranslator`：判定 UI 文本（`isUiText`）、翻译结果必须含中文才采用（v2.2.0）。
- `MissingKeysRecorder`：未翻译文本按模组归组导出 `missing_<modid>.txt`；已含中文的键值跳过不记。
- `ModConfig`：配置读写；含 `configVersion=2` 旧配置自动迁移（`applyLocalAiDefaults`），save 时写回 configVersion。

关键工程事实：
- 26.1 起 Minecraft 不再混淆，**直接使用 Mojang 官方类名**（如 `net.minecraft.locale.Language`）。
- `Language#getOrDefault(String,String)` 双参重载在 26.3 是抽象方法，**无法 Mixin 注入**（javap 验证过，此思路已废弃）。
- 所有 Mixin 注入点必须全防御 try-catch（v2.2.0 要求），任何异常都不得影响游戏。

---

## 4. 源码结构与文件职责

工程根：`D:\豆包工作区\自动汉化模组\AutoTranslateMod\`

```
src/main/java/com/autotranslate/
  AutoTranslateMod.java          # 主入口（main entrypoint）：初始化、扫描、词典加载
  ModConfig.java                 # 配置模型（config.json 读写 + v2 迁移）
  Dictionary.java                # 词典加载/查询（byKey/byText、剥离尾省略号）
  ModScanner.java                # mods 文件夹全量扫描 + 命名空间收集
  OnlineTranslator.java          # 本地/在线 AI 翻译（固定 2 线程、批量落盘 5s、failover、缓存）
  HardcodedTextTranslator.java   # 硬编码文本翻译（UI 判定、中文校验）
  MissingKeysRecorder.java       # 缺失键记录（按模组归组、中文跳过）
  mixin/LanguageMixin.java       # 语言键通道
src/client/java/com/autotranslate/client/
  AutoTranslateModClient.java    # 客户端入口
  AutoTranslateConfigScreen.java # v2.6.0：Cloth Config 静态工厂 create(Screen)
  ModMenuIntegration.java        # Mod Menu 集成（getModConfigScreenFactory → ::create）
  mixin/FontMixin.java           # 渲染层双通道（String + FCS）
src/main/resources/
  fabric.mod.json                # depends 已含 cloth-config >=26.3.0
  autotranslate.mixins.json
  assets/autotranslate/lang/zh_cn_dict.json     # 内置官方词典（byKey 8578 + byText 7655）
  assets/autotranslate/lang/extra_zh_cn.json    # 实测补全词典（byKey 740 + byText 90）
  assets/autotranslate/cfpa_mods.zip            # CFPA 翻译包（3295 条，注意：不在 lang/ 子目录）
libs/                              # 编译期依赖，.gitignore 排除，不入库不打包
  modmenu-21.0.0.jar             # ARR（compileOnly）
  cloth-config-fabric-26.3.159.jar  # LGPL-3.0（compileOnly，运行时必需）
local-translator/                  # Argos 本地服务的入库副本（service.py 等）
```

构建配置要点：
- `build.gradle` 中两个第三方 jar 必须用 **`compileOnly files("libs/xxx.jar")`**；`modImplementation files()` 在本工程报错「Could not find method modImplementation」，**勿用**。
- Loom 版本必须 **1.18.3**（1.12.31 不可用）。

---

## 5. 词典体系（加载优先级）

1. 内置官方词典 `zh_cn_dict.json`（byKey 8578 + byText 7655，提取自 MC 26.3 官方 en_us/zh_cn，版权归 Mojang）
2. CFPA 包 `cfpa_mods.zip`（3295 条，CC BY-NC-SA 4.0）
3. 内置补充 `extra_zh_cn.json`（byKey 740 + byText 90，本项目原创）
4. **外部词典目录**（递归）：`config/autotranslate/dict/`
   - `cache.json`：AI 译文回填缓存（持久化）
   - `cfpa*.zip`：外部 CFPA 多包（自动识别）
   - `missing_zh_cn.txt` / `missing_hardcoded.txt` / `missing_<modid>.txt`：缺失导出
   - 其他 json 词典自动加载
- 模组自带 `zh_cn.json` 优先于 CFPA（尊重模组自带翻译）。

改词典工作流（勿手改内置大文件后忘记重建）：
1. 写一个临时 python 脚本（放自选目录，用完可删）：读 extra_zh_cn.json → 合并新增 → 写回；
2. 重新 `gradlew build`；
3. 部署 + 发版。

---

## 6. 配置（config/autotranslate/config.json）

测试环境当前值（已设为本地 AI 默认）：
```json
{"configVersion":2,"enabled":true,"overrideExisting":true,
"externalDictDir":"autotranslate/dict","onlineTranslate":true,
"translateServiceUrl":"http://127.0.0.1:5000/translate?q=%s&source=en&target=zh",
"translateEngine":"argos","exportMissing":true,"deepTranslate":2}
```
字段：`enabled` 总开关；`overrideExisting` 词典优先；`onlineTranslate` 本地 AI 翻译；`translateEngine`（argos/google/auto）；`deepTranslate`（0 关 / 1 词典覆盖 / 2 全自动）；`exportMissing` 缺失导出；`translateServiceUrl` 服务地址（支持 `;` 分隔多引擎，必须含 `%s`）。

v2.6.0 配置界面为 **Cloth Config**（自带滚动/分类/下拉/撤销/重置/保存）；旧版自绘界面的「重载词典」「查看扫描报告」两个按钮已移除（启动自动扫描+加载，扫描报告只打日志），如用户需要可在 Cloth 中用自定义 entry 补回。

---

## 7. 本地 AI 服务（Argos Translate）

- 目录：`D:\豆包工作区\本地翻译服务\`（venv + `service.py`，端口 **5000**）；仓库副本 `AutoTranslateMod/local-translator/`。
- 安装：`install.bat`（首次需联网装包，Argos 新版 stanza 联网在国内被墙，**必须用 MiniSBD 分句**，已处理）；启动：`start.bat`（或 `service.py --port 5000`）。
- HTTP 接口：`GET http://127.0.0.1:5000/translate?q=<英文>&source=en&target=zh`，返回 JSON。
- 在线验证：`Test-NetConnection 127.0.0.1 -Port 5000`。
- 硬件要求：纯 CPU 可跑，无需独显；模型很小（translate-en_zh 约几十 MB），占用低。

---

## 8. 工具链与构建

- JDK：**D:\dev\jdk-25**（26.1+ 强制 Java 25）；Gradle 缓存：`D:\dev\gradle-home`；Loom 1.18.3。
- 构建命令（PowerShell，工程根）：
```powershell
$env:JAVA_HOME="D:\dev\jdk-25"; $env:GRADLE_USER_HOME="D:\dev\gradle-home"
.\gradlew.bat build --no-daemon --console=plain
```
产物：`build\libs\autotranslate-<ver>.jar`（另有 sources jar，发布用主 jar）。

---

## 9. 部署与发布 SOP

测试 mods 目录：`E:\MC\PCL 快照版 2.12.8.0\.minecraft\versions\26.3-Fabric 0.19.5\mods\`
发布包目录：`D:\豆包工作区\发布版本\`

1. 改 `gradle.properties` 的 `mod_version`；构建。
2. 复制 jar 到发布目录。
3. **部署前必查游戏进程**：`Get-Process java,javaw`；若 jar 被占用，删除会失败（Access denied 时杀进程也常被拒，需让用户**完全退出 PCL**）；必须**清掉旧版再复制新版，严禁双版本并存**（否则 Fabric 加载旧版）。
4. 部署后 SHA256 校验：`(Get-FileHash mods\autotranslate-x.jar).Hash` 与发布目录一致。
5. 更新 CHANGELOG.md（脚本模式：用 `s.find("## vX.X.X")` 切 tail + 重建头；`-replace` 正则常静默失败，勿用）。
6. git commit + push（内置 git：`C:\Users\zhoujiayi\AppData\Local\GitHubDesktop\app-3.6.5\resources\app\git\cmd\git.exe`）。
7. GitHub Release（REST）：
   - 取 token：`"protocol=https`nhost=github.com`n" | git credential fill`；
   - `POST https://api.github.com/repos/QWERTYUIOP733/autotranslate-cn/releases`（中文 body 必须 `[Text.Encoding]::UTF8.GetBytes()` 字节数组，否则 400）；
   - 用返回的 `upload_url` 上传 jar（`application/java-archive`）。
- 网络备注：github.com:443 偶发连接重置（api.github.com 通常可通），重试即可；已配置过 SSH（`~/.ssh/id_ed25519`，公钥需在 GitHub 账号中），22 端口可用，备用 `ssh.github.com:443`。

---

## 10. 测试环境与验收（Task #44，当前唯一主线）

测试 mods（10 个 jar，2026-10-10 轮换，**词典零收录，100% 依赖本地 AI 现场翻译**，禁止私自手工翻译它们）：

| 类型 | 模组 |
|---|---|
| 小地图+设置菜单 | voxelmap-fabric-26.3-1.16.13.jar（VoxelMap）、flowermap-fabric-26.3-2.0.11.jar（Flower Map） |
| 物品/武器 | weaponsexpanded_26.3_2.1.2_fabric.jar（Weapons Expanded） |
| 物品/背包 | nemos-backpacks-26.3-2.1.jar（Nemo's Backpacks） |
| 依赖 | cloth-config-fabric-26.3.159.jar、nemos-upgrade-templates-26.3-1.2.jar |
| 基础件 | fabric-api、fabric-language-kotlin、[模组菜单] modmenu-21.0.0、autotranslate-2.6.0 |

**三轮游戏内自检验收标准（全部中文才算通过）**：
1. 物品栏物品：Weapons Expanded / Nemo's Backpacks 的物品名/提示全中文；
2. 小地图设置菜单：VoxelMap / Flower Map 的设置界面全中文；
3. 配置文件配置菜单：各模组配置界面（Cloth/自渲染）全中文。

排查数据源（游戏跑过后）：
- `…\config\autotranslate\dict\cache.json` = AI 已翻译回填；
- `missing_*.txt` = 仍未翻译的文本（补词典/修通道的直接依据）；
- 启动日志有「已扫描模组清单」与词典来源统计。

---

## 11. 开源合规状态（重要，勿回退）

三层授权（代码与数据分离，README 有完整声明）：
- 模组代码：**MIT**（LICENSE / LICENSE_autotranslate）；
- CFPA 数据：**CC BY-NC-SA 4.0**（LICENSE_CFPA 随 jar，非商业、相同方式共享、署名）；
- 官方词典：版权归 **Mojang Studios / Microsoft**，仅本地化使用；
- 依赖：Mod Menu = **ARR**（仅编译期、不打包、不入库）；Cloth Config = **LGPL-3.0**（动态链接、可替换、运行时必需）；Argos = MIT 外部工具不打包。

2026-10-12 合规三轮自查并**重写全部 git 历史**（filter-branch + 19 个 tag + force push），已清除：
- 历史 blob 中的 `libs/modmenu-21.0.0.jar`（ARR）；
- 误入库的 MobLassos 解包污染（`fuzs/`、`assets/moblassos/`、`META-INF/`、根 `fabric.mod.json/pack.mcmeta`、`data/`、`.cache/` 等 110+ 文件）。
**教训：绝不能在工程根执行 `jar xf`；解包一律去 ASCII 临时目录。** 当前本地/远程历史与产物均验证无污染。

---

## 12. 已验证的硬性坑（接手者勿重试）

1. PowerShell 5.1：中文路径/中文参数传外部 exe（jar/javap/git）会乱码损坏 → 先拷到自建 ASCII 临时目录（如 `D:\modwork\`，用后可删）。
2. PS stdout 常被吞/乱码 → 高危/核查操作**落盘到该临时目录的 txt 再 Read**。
3. Python 内联 `-c` 带中文/三引号有坑 → 写成 `.py` 脚本文件（ASCII 路径）再执行。
4. 游戏运行中 jar 被锁删不掉；mods 双版本并存 → Fabric 加载旧版；部署前清旧版。
5. CFPA main 分支 `zh_cn.lang` 404；Modrinth API 查询参数必须 urlencode（`game_versions=["26.3"]` 裸传会 400）。
6. `Language.getOrDefault(String,String)` 26.3 为抽象方法，Mixin 无法注入 → 自渲染文本只能走 FCS 通道。
7. Configuration/PuzzlesLib 直接渲染 en_us Component 绕过语言系统；列表项截断渲染带尾部 `...`（如 "Aquatic Lasso..."），lookupText 已做剥离匹配。
8. `modImplementation files()` 不可用 → 一律 `compileOnly files()`；Loom 1.12.31 不可用 → 1.18.3。
9. CHANGELOG 更新用 `find("## vX")` 切 tail + 重建头，勿用 `-replace` 正则。
10. GitHub REST 传中文 body 必须 UTF-8 字节数组；git 网络重置时重试或改 SSH（22 / ssh.github.com:443）。

---

## 13. 下一步（恢复工作时按此顺序）

1. 确认本地 Argos 服务在线（`Test-NetConnection 127.0.0.1 -Port 5000`；不在线则 `start.bat`）。
2. 请用户启动游戏，完成 §10 三轮自检并截图；
3. 读 `config/autotranslate/dict/` 下 missing_*.txt：AI 未翻的 → 修通道（判定逻辑/服务地址）或补 extra 词典；
4. 按 §9 发版（递增版本号，如 v2.6.1 / v2.7.0）；
5. 三轮自检全部通过后关闭 Task #44，项目达到 v1.0 验收目标。

---

## 14. 关键路径速查

| 用途 | 路径 |
|---|---|
| 工程根 | `D:\豆包工作区\自动汉化模组\AutoTranslateMod\` |
| 发布 jar | `D:\豆包工作区\发布版本\autotranslate-2.6.0.jar` |
| 测试 mods | `E:\MC\PCL 快照版 2.12.8.0\.minecraft\versions\26.3-Fabric 0.19.5\mods\` |
| 测试 config | `…\26.3-Fabric 0.19.5\config\autotranslate\config.json` |
| 缺失/缓存词典 | `…\config\autotranslate\dict\` |
| 本地 AI 服务 | `D:\豆包工作区\本地翻译服务\`（venv，端口 5000） |
| 临时核验输出 | 自建 ASCII 临时目录（如 `D:\modwork\`，规避中文乱码，用后可删） |
| GitHub | `https://github.com/QWERTYUIOP733/autotranslate-cn` |
| 内置 git | `C:\Users\zhoujiayi\AppData\Local\GitHubDesktop\app-3.6.5\resources\app\git\cmd\git.exe` |
