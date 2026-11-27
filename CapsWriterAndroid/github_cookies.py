import json
import os
import urllib.request

import websocket

targets = json.loads(urllib.request.urlopen("http://127.0.0.1:9222/json/list", timeout=5).read())
page = next(t for t in targets if t.get("type") == "page" and "github.com" in t.get("url", ""))
ws = websocket.create_connection(page["webSocketDebuggerUrl"], timeout=15)
ws.send(json.dumps({"id": 1, "method": "Network.getAllCookies"}))
while True:
    message = json.loads(ws.recv())
    if message.get("id") == 1:
        break
ws.close()

cookies = {}
for cookie in message.get("result", {}).get("cookies", []):
    if cookie.get("domain", "").endswith("github.com"):
        cookies[cookie["name"]] = cookie["value"]
for name in ("user_session", "__Host-user_session_same_site", "_gh_sess"):
    print(name, "present" if name in cookies else "missing", len(cookies.get(name, "")))
header = "; ".join(f"{name}={value}" for name, value in cookies.items())
path = os.path.join(os.path.dirname(__file__), ".github_cookie")
with open(path, "w", encoding="utf-8") as handle:
    handle.write(header)
print("cookie_header_len", len(header), "names", sorted(cookies))
