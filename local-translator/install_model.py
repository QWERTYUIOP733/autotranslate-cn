# -*- coding: utf-8 -*-
"""下载并安装 en->zh 语言包（一次性）"""
import argostranslate.package as ap

print("[1/2] 更新语言包索引 ...")
ap.update_package_index()

avail = ap.get_available_packages()
zh = [p for p in avail if p.from_code == "en" and p.to_code == "zh"]
if not zh:
    print("错误：语言包索引中未找到 en->zh 包，请检查网络或稍后重试")
    raise SystemExit(1)
pkg = zh[0]
print(f"[2/2] 下载并安装: {pkg} ...")
ap.install_from_path(pkg.download())
print("完成！现在运行 start.bat 启动翻译服务")
