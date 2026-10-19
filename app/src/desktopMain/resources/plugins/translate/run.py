# Translates the text into the language picked from the submenu: CROSSPASTE_ACTION carries
# the chosen action id, which is a Google language code.
# Uses only the Python standard library, which desktop Linux distributions ship.
# stdin: the paste as JSON (same shape as `crosspaste paste --json`).
# stdout: the translation, UTF-8. A non-zero exit reports stderr to the user.
import json
import os
import sys
import urllib.parse
import urllib.request


def translate(text, target):
    # The scheme and host are fixed; only the query carries the (quoted) language and text
    url = (
        "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl="
        + urllib.parse.quote(target)
        + "&dt=t&q="
        + urllib.parse.quote(text)
    )
    with urllib.request.urlopen(url, timeout=20) as response:  # nosec B310
        result = json.loads(response.read().decode("utf-8"))
    return "".join(segment[0] for segment in result[0])


paste = json.loads(sys.stdin.buffer.read().decode("utf-8"))
text = paste.get("content") or paste.get("rawContent") or ""
target = os.environ.get("CROSSPASTE_ACTION") or "en"
try:
    translated = translate(text, target)
except Exception as e:
    # CrossPaste shows stderr to the user; one line reads better than a traceback
    sys.exit(str(e))
sys.stdout.buffer.write(translated.encode("utf-8"))
