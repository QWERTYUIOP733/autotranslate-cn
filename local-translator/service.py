# -*- coding: utf-8 -*-
"""本地 AI 翻译服务（Argos Translate）
为「自动汉化模组」提供离线翻译 API：
    GET /translate?q=英文&source=en&target=zh
    -> {"translatedText": "中文"}
纯 CPU 推理，无需 GPU/显存。仅监听 127.0.0.1。
"""
import argparse
import json
import os
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

# 关键：使用本地 MiniSBD 分句器（纯本地，无需联网下载 stanza 资源）
os.environ.setdefault("ARGOS_CHUNK_TYPE", "MINISBD")

import argostranslate.package as ap
import argostranslate.translate as at

_engines = {}  # (src,tgt) -> Translation


def get_engine(src, tgt):
    key = (src, tgt)
    if key not in _engines:
        tr = at.get_translation_from_codes(src, tgt)
        if tr is None:
            raise RuntimeError(f"未安装 {src}->{tgt} 语言包，请先运行 install_model.bat")
        _engines[key] = tr
    return _engines[key]


class Handler(BaseHTTPRequestHandler):
    def _handle(self, q, src, tgt):
        if not q:
            return {"translatedText": ""}
        # 长文本/多行也支持；模组侧的短文本场景完全够用
        tr = get_engine(src or "en", tgt or "zh")
        out = tr.translate(q)
        return {"translatedText": out}

    def do_GET(self):
        u = urlparse(self.path)
        if u.path != "/translate":
            self.send_error(404, "not found")
            return
        qs = parse_qs(u.query)
        try:
            data = self._handle(qs.get("q", [""])[0], qs.get("source", ["en"])[0], qs.get("target", ["zh"])[0])
            self._json(200, data)
        except Exception as e:
            self._json(500, {"error": str(e)})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        try:
            body = json.loads(self.rfile.read(length).decode("utf-8")) if length else {}
        except Exception:
            self._json(400, {"error": "bad json"})
            return
        try:
            data = self._handle(body.get("q", ""), body.get("source", "en"), body.get("target", "zh"))
            self._json(200, data)
        except Exception as e:
            self._json(500, {"error": str(e)})

    def _json(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *a):
        pass


def main():
    ap_ = argparse.ArgumentParser(description="本地 AI 翻译服务")
    ap_.add_argument("--port", type=int, default=5000)
    ap_.add_argument("--host", default="127.0.0.1")
    args = ap_.parse_args()

    # 预加载语言包，避免首次请求慢
    try:
        ap.update_package_index()
    except Exception:
        pass
    try:
        get_engine("en", "zh")
        print("[本地翻译] en->zh 模型加载成功")
    except Exception as e:
        print(f"[本地翻译] 警告：{e}（未装模型也能启动，翻译时会报错提示）")

    server = HTTPServer((args.host, args.port), Handler)
    print(f"[本地翻译] 服务已启动：http://{args.host}:{args.port}/translate")
    print("[本地翻译] 在模组配置中把翻译引擎设为「本地 Argos」，")
    print(f"[本地翻译] 服务地址填：http://{args.host}:{args.port}/translate?q=%s&source=en&target=zh")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[本地翻译] 已停止")


if __name__ == "__main__":
    main()
