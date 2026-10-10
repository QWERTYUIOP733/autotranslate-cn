# 自动汉化模组 更新日志

## v2.4.2 (2026-10-10)

### 🐛 修复：GUI 列表项截断省略号导致未命中（根因）
- 实测发现 MobLassos 配置屏套索名称渲染为 `Aquatic Lasso...`（Configuration 库列表项截断自动加省略号），
  词典存 `Aquatic Lasso` → 之前一直未命中
- **lookupText 全局归一化**：未命中时剥离尾部 `...` 再匹配（FCS / String / 语言键三通道同时生效，
  任何模组的截断文本都能命中，属通用修复）
- 词典补入面包屑路径与描述：Mob Lassos Settings > Golden Lasso（生物套索设置 > 黄金套索）等 8 条

# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [v26.3.0-mc26.3.x] - 2026-09-24

### Changed

- Update to Minecraft 26.3.x
