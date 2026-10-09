# 自动汉化模组 更新日志

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
