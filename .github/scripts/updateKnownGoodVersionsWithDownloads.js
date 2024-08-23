const fs = require('fs');
const axios = require('axios');

function convertUrl(url) {
    const match = url.match(/\/(\d+\.\d+\.\d+\.\d+)\/(.+)\/(.+\.zip)/);
    if (match) {
        const [, version, platform, filename] = match;
        return `https://cdn.npmmirror.com/binaries/chrome-for-testing/${version}/${platform}/${filename}`;
    }
    return url;
}

function processJson(inputJson) {
    const processedJson = JSON.parse(JSON.stringify(inputJson));

    processedJson.versions.forEach(version => {
        if (version.downloads) {
            // Process chromedriver
            if (version.downloads['chromedriver']) {
                version.downloads['chromedriver'].forEach(item => {
                    item.url = convertUrl(item.url);
                });
            }

            // Process chrome-headless-shell
            if (version.downloads['chrome-headless-shell']) {
                version.downloads['chrome-headless-shell'].forEach(item => {
                    item.url = convertUrl(item.url);
                });
            }

            // Process chrome
            if (version.downloads['chrome']) {
                version.downloads['chrome'].forEach(item => {
                    item.url = convertUrl(item.url);
                });
            }

            // Keep only chromedriver, chrome-headless-shell, and chrome
            version.downloads = {
                'chromedriver': version.downloads['chromedriver'] || [],
                'chrome-headless-shell': version.downloads['chrome-headless-shell'] || [],
                'chrome': version.downloads['chrome'] || []
            };
        }
    });

    return processedJson;
}

async function fetchAndProcessJson() {
    const url = "https://raw.githubusercontent.com/GoogleChromeLabs/chrome-for-testing/main/data/known-good-versions-with-downloads.json";

    try {
        console.log(`Fetching JSON from: ${url}`);
        const response = await axios.get(url, { timeout: 10000 });
        console.log(`Status Code: ${response.status}`);

        const inputJson = response.data;
        const processedJson = processJson(inputJson);

        fs.writeFileSync('known-good-versions-with-downloads.json', JSON.stringify(processedJson, null, 2));
        console.log("JSON processing complete. Output saved to known-good-versions-with-downloads.json");
    } catch (error) {
        if (error.response) {
            // The request was made and the server responded with a status code that falls out of the range of 2xx
            console.error(`HTTP error! status: ${error.response.status}`);
        } else if (error.request) {
            // The request was made but no response was received
            console.error('No response received:', error.message);
        } else {
            // Something happened in setting up the request that triggered an Error
            console.error('Error:', error.message);
        }
        throw error;
    }
}

// If the script is run directly, execute the processing
if (require.main === module) {
    fetchAndProcessJson().catch(error => {
        console.error("Script failed:", error);
        process.exit(1);
    });
}

module.exports = { processJson, fetchAndProcessJson };