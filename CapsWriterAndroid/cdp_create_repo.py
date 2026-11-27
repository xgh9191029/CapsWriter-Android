import json
import time
import urllib.request

import websocket

TARGETS = json.loads(urllib.request.urlopen("http://127.0.0.1:9222/json/list", timeout=5).read())
page = next(t for t in TARGETS if t.get("type") == "page" and "github.com" in t.get("url", ""))
ws = websocket.create_connection(page["webSocketDebuggerUrl"], timeout=15)
seq = 0


def cmd(method, **params):
    global seq
    seq += 1
    ws.send(json.dumps({"id": seq, "method": method, "params": params}))
    while True:
        message = json.loads(ws.recv())
        if message.get("id") == seq:
            if "error" in message:
                raise RuntimeError(message["error"])
            return message.get("result", {})


def js(expression):
    result = cmd("Runtime.evaluate", expression=expression, returnByValue=True)
    return result.get("result", {}).get("value")


def click_xy(x, y):
    cmd("Input.dispatchMouseEvent", type="mouseMoved", x=x, y=y)
    cmd("Input.dispatchMouseEvent", type="mousePressed", x=x, y=y, button="left", clickCount=1)
    cmd("Input.dispatchMouseEvent", type="mouseReleased", x=x, y=y, button="left", clickCount=1)


cmd("Page.bringToFront")
# Navigate to a clean form.
cmd("Page.navigate", url="https://github.com/new")
time.sleep(3)

input_rect = js("(() => {const e=document.querySelector('#repository-name-input'); if(!e) return null; const r=e.getBoundingClientRect(); return {x:r.left+r.width/2,y:r.top+r.height/2};})()")
if not input_rect:
    raise SystemExit("repo-name input not found")
click_xy(input_rect["x"], input_rect["y"])
js("document.querySelector('#repository-name-input').select()")
cmd("Input.insertText", text="CapsWriter-Android")
time.sleep(1)
print("name after typing:", js("document.querySelector('#repository-name-input').value"))

desc_rect = js("(() => {const e=document.querySelector('input[name=\"Description\"]'); if(!e) return null; const r=e.getBoundingClientRect(); return {x:r.left+r.width/2,y:r.top+r.height/2};})()")
if desc_rect:
    click_xy(desc_rect["x"], desc_rect["y"])
    js("document.querySelector('input[name=\"Description\"]').select()")
    cmd("Input.insertText", text="Unofficial Kotlin Android client for CapsWriter-Offline. Upstream: https://github.com/HaujetZhao/CapsWriter-Offline")
    time.sleep(1)
    print("desc after typing:", js("document.querySelector('input[name=\"Description\"]').value"))

button_rect = js("(() => {const b=[...document.querySelectorAll('button')].find(e=>e.innerText.includes('创建存储库')); if(!b) return null; const r=b.getBoundingClientRect(); return {x:r.left+r.width/2,y:r.top+r.height/2,disabled:b.disabled};})()")
print("button:", button_rect)
if button_rect and not button_rect.get("disabled"):
    click_xy(button_rect["x"], button_rect["y"])
time.sleep(5)
print("url:", js("location.href"))
print("title:", js("document.title"))
print("alerts:", js("[...document.querySelectorAll('[role=alert],.flash-error')].map(e=>e.innerText).slice(0,4)"))
ws.close()
