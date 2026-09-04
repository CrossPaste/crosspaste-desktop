#!/usr/bin/env python3
"""Minimal MCP stdio client: runs a command, sends initialize + tools/list, prints results."""
import json, subprocess, sys, threading, time

cmd = sys.argv[1:]
p = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, bufsize=1)

def drain_stderr():
    for line in p.stderr:
        sys.stderr.write("[stderr] " + line)
threading.Thread(target=drain_stderr, daemon=True).start()

def send(obj):
    p.stdin.write(json.dumps(obj) + "\n"); p.stdin.flush()

def recv(timeout=120):
    deadline = time.time() + timeout
    while time.time() < deadline:
        line = p.stdout.readline()
        if not line:
            if p.poll() is not None:
                raise SystemExit(f"process exited with {p.returncode}")
            time.sleep(0.1); continue
        line = line.strip()
        if not line: continue
        try:
            return json.loads(line)
        except json.JSONDecodeError:
            sys.stderr.write("[stdout-noise] " + line + "\n")
    raise SystemExit("timeout waiting for response")

send({"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"probe","version":"0"}}})
init = recv()
print("initialize ->", json.dumps(init.get("result", init), indent=None)[:400])
send({"jsonrpc":"2.0","method":"notifications/initialized"})
send({"jsonrpc":"2.0","id":2,"method":"tools/list"})
tools = recv()
names = [t["name"] for t in tools.get("result", {}).get("tools", [])]
print("tools/list ->", names)
send({"jsonrpc":"2.0","id":3,"method":"resources/list"})
res = recv()
print("resources/list ->", [r["name"] for r in res.get("result", {}).get("resources", [])])
p.stdin.close()
try: p.wait(timeout=10)
except subprocess.TimeoutExpired: p.kill()
sys.exit(0 if names else 1)
