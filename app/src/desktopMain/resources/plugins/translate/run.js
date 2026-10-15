// Translates the text into the CrossPaste UI language (CROSSPASTE_LANGUAGE); text already
// in that language goes to English instead.
// JavaScript for Automation ships with every macOS, so the plugin needs no extra tools.
// stdin: the paste as JSON (same shape as `crosspaste paste --json`).
// stdout: the value run() returns. A thrown error exits non-zero and is shown to the user.
// Cocoa rather than Foundation: the Foundation bridge alone does not expose the URL loading classes
ObjC.import('Cocoa');

// CrossPaste language codes that differ from Google's
const GOOGLE_CODES = { zh: 'zh-CN', zh_hant: 'zh-TW' };

function readStdin() {
    const data = $.NSFileHandle.fileHandleWithStandardInput.readDataToEndOfFile;
    return $.NSString.alloc.initWithDataEncoding(data, $.NSUTF8StringEncoding).js;
}

function fetchText(url) {
    const request = $.NSMutableURLRequest.requestWithURL($.NSURL.URLWithString(url));
    request.timeoutInterval = 20;
    const response = $();
    const error = $();
    const data = $.NSURLConnection.sendSynchronousRequestReturningResponseError(request, response, error);
    if (data.isNil()) {
        throw ObjC.unwrap(error.localizedDescription);
    }
    if (Number(response.statusCode) !== 200) {
        throw 'HTTP ' + response.statusCode;
    }
    return $.NSString.alloc.initWithDataEncoding(data, $.NSUTF8StringEncoding).js;
}

function translate(text, target) {
    const url = 'https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=' + target +
        '&dt=t&q=' + encodeURIComponent(text);
    const response = JSON.parse(fetchText(url));
    return {
        text: response[0].map(function (segment) { return segment[0]; }).join(''),
        source: response[2] || '',
    };
}

function run() {
    const paste = JSON.parse(readStdin());
    const text = paste.content || paste.rawContent || '';
    const language = ObjC.unwrap($.NSProcessInfo.processInfo.environment.objectForKey('CROSSPASTE_LANGUAGE')) || 'en';
    const target = GOOGLE_CODES[language] || language;
    const result = translate(text, target);
    // Detection is unreliable (Traditional Chinese can come back as "en"), so an
    // unchanged result also means the text is already in the target language
    const alreadyTarget = result.source.split('-')[0] === target.split('-')[0] || result.text.trim() === text.trim();
    if (alreadyTarget && target !== 'en') {
        return translate(text, 'en').text;
    }
    return result.text;
}
