# Translates Chinese text to English and anything else to Simplified Chinese.
# Uses only the Python standard library, which desktop Linux distributions ship.
# stdin: the paste as JSON (same shape as `crosspaste paste --json`).
# stdout: the translation, UTF-8. A non-zero exit reports stderr to the user.
import json
import re
import sys
import urllib.parse
import urllib.request

paste = json.loads(sys.stdin.buffer.read().decode("utf-8"))
text = paste.get("content") or paste.get("rawContent") or ""
target = "en" if re.search("[\u3400-\u9fff]", text) else "zh-CN"
url = (
    "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl="
    + target
    + "&dt=t&q="
    + urllib.parse.quote(text)
)
try:
    with urllib.request.urlopen(url, timeout=20) as response:
        result = json.loads(response.read().decode("utf-8"))
except Exception as e:
    # CrossPaste shows stderr to the user; one line reads better than a traceback
    sys.exit(str(e))
sys.stdout.buffer.write("".join(segment[0] for segment in result[0]).encode("utf-8"))
