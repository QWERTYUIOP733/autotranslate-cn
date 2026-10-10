# 自动汉化模组 更新日志

## v2.4.0 (2026-10-10)

### 🎯 10 代迭代收官（v1.6.0 → v2.4.0）
- 第 1 代 v1.6.0：在线翻译兜底加固（批量落盘 / 固定线程池 / 瞬时重试 / 启动探测 / 连续失败告警）
- 第 2 代 v1.7.0：byText 大小写不敏感命中 + FCS 通道未命中补记录（词典闭环）
- 第 3 代 v1.8.0：扫描模组清单日志 + 缺失键按模组归组导出（missing_<modid>.txt）
- 第 4 代 v1.9.0：FCS / String 渲染通道翻译结果 LRU 缓存（性能优化）
- 第 5 代 v1.10.0：翻译质量体系（Minecraft 术语表后处理 + 多引擎 failover，服务地址分号分隔）
- 第 6 代 v2.0.0：配置界面增强（导出缺失键开关 / 服务地址编辑框 / 查看扫描报告按钮）
- 第 7 代 v2.1.0：词典体系完善（外部词典子目录递归 / 外部 CFPA 多包 / 来源统计日志）
- 第 8 代 v2.2.0：稳定性加固（译文必须含中文才采用 / 注入点全防御 try-catch）
- 第 9 代 v2.3.0：稳定性加固二（URL 模板告警仅一次 / String 通道命中缓存）
- 第 10 代 v2.4.0：全量验证 + 文档与发布

### 📦 安装要求
- Minecraft 26.3（Java 25）、Fabric Loader 0.19.5+、Fabric API 0.161.0+26.3

## v1.7.0 (2026-10-10)

### ✨ 功能
- **byText 大小写不敏感匹配**：词典英文原文忽略大小写命中（"iron sword" 也能查到 "Iron Sword"），新增小写索引并随在线翻译增量更新
- **FCS 渲染通道未命中文本补记录**：配置界面等组件渲染的未覆盖英文会写入 missing_hardcoded.txt（经 UI 文本特征过滤，聊天/世界内容不记录），词典补全形成闭环

### 🔧 技术更新
- 核实 26.3 Language 双参 getOrDefault(String,String) 为抽象方法（Mixin 无法注入），确认 Configuration 库自渲染 en_us 路径由 FCS 通道覆盖
- isUiText 过滤器公开供渲染层多通道复用

## v1.6.0 (2026-10-10)

### 🔧 技术更新（在线翻译兜底加固）
- 翻译结果改为**定时批量落盘**（每 5 秒合并写入 cache.json），避免大量缺失键时每次全量 IO
- 翻译线程池由无界改为**固定 2 线程**，防止缺失键风暴引发无限并发
- **瞬时失败自动重试 1 次**（网络抖动/服务重启中不再直接放弃）
- **启动时自动探测翻译服务**：加载即检测本地/在线翻译服务可用性并输出日志，解决此前"兜底静默失败、cache 不增长"难排查的问题
- **连续 5 次失败输出告警**并自动降级为英文（不中断游戏）
- 翻译服务地址模板缺失 %s 参数位时告警

## v1.5.2 (2026-10-10)
 (2026-10-10)

### ✨ 功能
- 词典扩充 60 条手工翻译：Phoenix（凤凰老虎机，35 键：物品/UI 按钮/配置项）与 Mob Lassos（生物套索，25 键：物品/附魔/音效/物品组）
- 修复本地翻译服务多实例重复启动问题，恢复在线兜底（单实例 127.0.0.1:5000）

## v1.5.1 (2026-10-10)
 (2026-10-10)

### 🔧 合规整改
- 开源合规自查整改：README 新增「授权与版权」小节，明确三层授权——模组代码 MIT / CFPA 数据 CC BY-NC-SA 4.0（随 jar 分发 LICENSE_CFPA）/ 官方词典版权归 Mojang；补充翻译（661 条）与缓存结果标注为原创
- fabric.mod.json 补全 authors 元数据
- 本地 AI 工具（Argos Translate，MIT）明确标注为第三方外部依赖

## v1.5.0 (2026-10-10)

### ✨ 功能
- **集成本地 AI 翻译引擎（Argos Translate）**：模组新增「翻译引擎」配置（自动识别 / 在线 Google / 本地 Argos），可接入本地部署的离线翻译服务——纯 CPU 推理、无需 GPU 显存、完全离线
- 翻译服务地址可配置，一键指向本地服务；本地引擎与在线 Google 协议自动识别，无缝切换
- 随模组配套发布「本地翻译服务」工具（install.bat / start.bat / service.py，D 盘），双击即可搭起本地 AI 翻译

### 🔧 技术更新
- OnlineTranslator 支持双协议解析：Google gtx（嵌套数组）与 Argos（translatedText JSON），auto 模式按响应结构自动识别
- 本地服务使用 MiniSBD 分句器（纯本地，无需联网下载 stanza 资源）
- 实测：Iron Sword→铁剑、Ignore Enforced As Editor→忽略已执行的编辑器、Hop to specified coordinates→跳转到指定的坐标

### 📦 安装要求
- Minecraft 26.3（Java 25）、Fabric Loader 0.19.5+、Fabric API 0.161.0+26.3
- 本地 AI 翻译为可选功能：需 Python 3.9+（仅首次安装，服务运行时约 1GB 内存）

## v1.4.1 (2026-10-10)

### ✨ 功能
- **深度汉化（渲染层拦截）**：补上最后一块死角——不走翻译系统的硬编码英文文本（如 Xaero 设置里的 "Ignore Enforced As Editor"）也会被翻译。配置界面新增三档：关 / 词典覆盖（只翻已收录，零风险）/ 全自动（未收录短文本也联网翻译）
- **ModScanner 全面适配**：改为扫描模组 jar 内**所有资源命名空间**的翻译文件并合并——兼容 modid 与命名空间不一致的模组（如 cloth-config 的命名空间是 cloth-config2）、嵌套库等一切情况，配置库（Cloth Config 等）自带中文也会自动加载
- 硬编码文本单独导出 `missing_hardcoded.txt`，反馈给开发者即可永久内置

### 🔧 技术更新
- 新增 FontMixin（客户端 Mixin，注入 Font#prepareText(String) 渲染入口，聊天/玩家名走 FormattedCharSequence 不受影响）、HardcodedTextTranslator（UI 文本特征过滤 + 词典命中 + 档位控制）
- 翻译覆盖链完整闭环：翻译键 → 英文原文 → **渲染层硬编码文本** → 在线翻译兜底

### 📦 安装要求
- Minecraft 26.3（Java 25）、Fabric Loader 0.19.5+、Fabric API 0.161.0+26.3

## v1.4.0 (2026-10-10)

### ✨ 功能
- **在线翻译兜底**：配置界面新增「在线翻译」开关。词典未覆盖的英文文本会自动联网翻译（异步、不卡游戏），结果立即生效并缓存到 `config/autotranslate/dict/cache.json`，下次启动自动加载
- **缺失键自动导出**：运行时遇到的未翻译文本自动记录到 `config/autotranslate/dict/missing_zh_cn.txt`（格式与 .lang 一致），改名放入词典目录即可自制翻译，或反馈给开发者内置补全
- **外部 CFPA 包覆盖**：`config/autotranslate/cfpa_mods.zip` 优先于内置 CFPA 数据——放入更新的 CFPA 全量包即可随时刷新数据源，无需重装模组

### 🔧 技术更新
- 新增 OnlineTranslator（异步翻译 + 限流 + 去重 + 失败静默降级）、MissingKeysRecorder（定时导出）
- 翻译服务地址可配置（config.json 的 translateServiceUrl，默认 Google 免费接口）

## v1.3.2 (2026-10-10)

### ✨ 功能
- **全模组补充翻译**：批量补全测试环境 7 个非中文模组的缺失翻译（581 条）——JEI 353、钠 105、自动攻击 51、Xaero 小地图 46、MezzConfig 18、Iris 7、模组菜单 1
- 补充翻译累计 **661 条**（含 v1.3.1 的 Xaero 世界地图 80 条）

### 🔧 技术更新
- 自动扫描汉化能力扩展：除 CFPA 全量包（1606 模组）+ 官方词典外，手工维护缺失键库
- 词典规模：官方 16,233 + 模组扫描增量 + 补充翻译 661 条

## v1.3.1 (2026-10-10)

### ✨ 功能
- **内置补充翻译**：手工翻译 80 条 CFPA 尚未收录的模组新键（覆盖 Xaero 世界地图 26.3 新增的「跳转到指定坐标」、区块认领（PAC）提示、洞穴模式、相机附着等）
- **外部词典支持 .lang 格式**：`config/autotranslate/dict/` 目录下 `.lang`（key=value）与 `.json` 均可自动加载，方便放入 CFPA 更新包

### 🔧 技术更新
- 词典加载顺序优化：官方词典 → 补充翻译 → 模组扫描（CFPA/自带）→ 外部词典
- 词典规模：官方 16,233 条 + 模组扫描增量 + 补充翻译

## v1.3.0 (2026-10-10)

### ✨ 功能
- **【核心功能】自动扫描汉化 mods 文件夹中的全部其他模组**：启动时自动检测 mods 目录下所有模组（自动识别 mod id），扫描其界面文本并汉化
  - 内置 **CFPA 模组翻译包**（Minecraft Mod Language Package，1600+ 模组翻译数据，CC BY-NC-SA 4.0）
  - 优先使用模组自带中文翻译（如 JEI、苹果皮自带 zh_cn.json），无自带时用 CFPA 数据
  - 自动生成「英文原文 → 中文」匹配（byText），直接写死英文文本的界面也能汉化
  - 热重载后自动重新扫描（配置界面「重载词典」或 `/autotranslate reload`）

### 🔧 技术更新
- 新增 ModScanner 自动扫描器（读模组 fabric.mod.json、解析 .lang/.json 语言文件、按翻译键配对生成原文匹配）
- 内置 CFPA 翻译包（约 7MB），发布包体积增至约 6.8MB
- 词典优先级：官方内置 < 模组自带/CFPA < 用户外部词典（用户自定义最优先）

### 📦 安装要求
- 无需额外配置，放入 mods 文件夹即自动扫描汉化其他模组
- 内置数据来自 CFPA 模组翻译包（CC BY-NC-SA 4.0，非商业用途）

## v1.2.0 (2026-10-10)

### ✨ 功能
- **Mod Menu 配置界面**：模组菜单中点击「配置」即可可视化设置，无需再改命令行或配置文件
  - 总开关（开 / 关）
  - 覆盖策略（仅补缺 / 词典优先）
  - 一键重载词典（替代 `/autotranslate reload`）
  - 修改即时保存到 `config/autotranslate/config.json`，下次启动仍生效
- 使用原版 GUI 控件实现，**不依赖 Cloth Config 等额外配置库**

### 🔧 技术更新
- 新增 Mod Menu 集成入口（compileOnly 依赖，未装 Mod Menu 不影响功能，命令 `/autotranslate reload|status` 仍保留可用）
- 适配 Minecraft 26.3 GUI 重构（`Minecraft.setScreenAndShow`、`CycleButton.Builder` 等新 API）

### 📦 安装要求
- 可选安装 Mod Menu 以使用配置界面（推荐）；不装则继续使用命令/配置文件

## v1.1.0 (2026-10-10)

### ✨ 功能
- **内置词典全面补全**：由 Minecraft 26.3 官方语言文件生成，byKey 8,578 条 + byText 7,655 条，覆盖官方全部翻译键
- 词典数据独立仓库管理：QWERTYUIOP733/autotranslate-dict（一键复现生成）

### 🔧 技术更新
- 内置词典体积升级至约 938 KB，启动加载逻辑不变
- 保留自定义条目优先机制（用户修正优先于官方翻译）

## v1.0.0 (2026-10-09)

### 🔧 技术更新
- 基于 **Minecraft 26.3**（Wilderness Bound）构建，Fabric Loader 0.19.5 + Fabric API 0.162.0+26.3
- Mixin 拦截 `net.minecraft.locale.Language#getOrDefault`，实现「词典优先 + 仅补缺」双策略拦截
- 内置翻译字典（byKey / byText 双结构），离线即用，无需联网翻译

### ✨ 功能
- **离线自动汉化**：游戏内未翻译文本自动替换为词典中的中文
- **外部词典扩展**：`config/autotranslate/dict/*.json` 放入任意结构词典，重启或热重载即时生效
- **热重载命令**：`/autotranslate reload` 重新加载配置与外部词典，`/autotranslate status` 查看当前状态
- **配置开关**：`config/autotranslate/config.json` 可调整 `enabled` / `overrideExisting` / `externalDictDir`

### 📦 安装要求
- Minecraft 26.3（Java 25）
- Fabric Loader 0.19.5+
- Fabric API 0.161.0+26.3（推荐 0.162.0+26.3）

## 第三方参考 / Third-party References
本项目开发过程中参考了以下项目的实现思路，代码均为独立编写：
1. **Fabric Example Mod** — 官方模板，用于项目骨架与构建配置
2. **Mixin 官方文档** — 用于翻译拦截器的注入设计
