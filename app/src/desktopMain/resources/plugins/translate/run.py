# Translates the text into the CrossPaste UI language (CROSSPASTE_LANGUAGE); text already
# in that language goes to English instead.
# Uses only the Python standard library, which desktop Linux distributions ship.
# stdin: the paste as JSON (same shape as `crosspaste paste --json`).
# stdout: the translation, UTF-8. A non-zero exit reports stderr to the user.
import json
import os
import sys
import urllib.parse
import urllib.request

# CrossPaste language codes that differ from Google's
GOOGLE_CODES = {"zh": "zh-CN", "zh_hant": "zh-TW"}


def translate(text, target):
    # The scheme and host are fixed; only the query carries the (quoted) text
    url = (
        "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl="
        + target
        + "&dt=t&q="
        + urllib.parse.quote(text)
    )
    with urllib.request.urlopen(url, timeout=20) as response:  # nosec B310
        result = json.loads(response.read().decode("utf-8"))
    return "".join(segment[0] for segment in result[0]), result[2] or ""


paste = json.loads(sys.stdin.buffer.read().decode("utf-8"))
text = paste.get("content") or paste.get("rawContent") or ""
language = os.environ.get("CROSSPASTE_LANGUAGE") or "en"
target = GOOGLE_CODES.get(language, language)
try:
    translated, source = translate(text, target)
    # Detection is unreliable (Traditional Chinese can come back as "en"), so an
    # unchanged result also means the text is already in the target language
    already_target = source.split("-")[0] == target.split("-")[0] or translated.strip() == text.strip()
    if already_target and target != "en":
        translated, _ = translate(text, "en")
except Exception as e:
    # CrossPaste shows stderr to the user; one line reads better than a traceback
    sys.exit(str(e))
sys.stdout.buffer.write(translated.encode("utf-8"))
