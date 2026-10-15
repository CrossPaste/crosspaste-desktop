// Translates the text into the language picked from the submenu: CROSSPASTE_ACTION carries
// the chosen action id, which is a Google language code.
// JavaScript for Automation ships with every macOS, so the plugin needs no extra tools.
// stdin: the paste as JSON (same shape as `crosspaste paste --json`).
// stdout: the value run() returns. A thrown error exits non-zero and is shown to the user.
// Cocoa rather than Foundation: the Foundation bridge alone does not expose the URL loading classes
ObjC.import('Cocoa');

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
    const url = 'https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=' +
        encodeURIComponent(target) + '&dt=t&q=' + encodeURIComponent(text);
    const response = JSON.parse(fetchText(url));
    return response[0].map(function (segment) { return segment[0]; }).join('');
}

function run() {
    const paste = JSON.parse(readStdin());
    const text = paste.content || paste.rawContent || '';
    const target = ObjC.unwrap($.NSProcessInfo.processInfo.environment.objectForKey('CROSSPASTE_ACTION')) || 'en';
    return translate(text, target);
}
