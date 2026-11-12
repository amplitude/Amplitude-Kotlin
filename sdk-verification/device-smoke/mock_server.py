#!/usr/bin/env python3
"""Mock Amplitude endpoints for the unified device smoke. Logs every request as JSON lines.

Usage: python3 mock_server.py --port 8089 --log requests.jsonl
"""
import argparse
import base64
import gzip
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

VARIANTS = {"checkout": {"key": "treatment", "value": "treatment"}}


def decode_body(raw, headers):
    if not raw:
        return None
    if headers.get("Content-Encoding", "").lower() == "gzip" or raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    try:
        return json.loads(raw)
    except ValueError:
        return {"_raw_length": len(raw)}


class Handler(BaseHTTPRequestHandler):
    log_path = "requests.jsonl"

    def _record(self, body=None):
        url = urlparse(self.path)
        entry = {
            "method": self.command,
            "path": url.path,
            "query": {k: v[0] for k, v in parse_qs(url.query).items()},
            "headers": {key.lower(): value for key, value in self.headers.items()},
            "body": body,
        }
        user = self.headers.get("X-Amp-Exp-User")
        if user:
            padded = user + "=" * (-len(user) % 4)
            entry["experiment_user"] = json.loads(base64.urlsafe_b64decode(padded))
        with open(self.log_path, "a") as log:
            log.write(json.dumps(entry) + "\n")
        return url.path

    def _reply(self, status, payload):
        data = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/health":
            return self._reply(200, {})
        path = self._record()
        if path.endswith("/sdk/v2/vardata"):
            self._reply(200, VARIANTS)
        elif path.endswith("/sdk/v2/flags"):
            self._reply(200, [])
        else:
            self._reply(404, {"error": "unknown path"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = decode_body(self.rfile.read(length), self.headers)
        path = self._record(body)
        if path == "/2/httpapi":
            events = len(body.get("events", [])) if isinstance(body, dict) else 0
            self._reply(200, {"code": 200, "events_ingested": events})
        elif path == "/sessions/v2/track":
            self._reply(200, {})
        else:
            self._reply(404, {"error": "unknown path"})

    def log_message(self, fmt, *args):
        print("%s %s" % (self.command, self.path), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8089)
    parser.add_argument("--log", default="requests.jsonl")
    args = parser.parse_args()
    Handler.log_path = args.log
    ThreadingHTTPServer(("127.0.0.1", args.port), Handler).serve_forever()
