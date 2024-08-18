const fs = require('fs');
const axios = require('axios');

function convertUrl(url) {
    const match = url.match(/\/(\d+\.\d+\.\d+\.\d+)\/.+?\/(.+\.zip)/);
    if (match) {
        const [, version, filename] = match;
        return `https://mirrors.huaweicloud.com/chromedriver/${version}/${filename}`;
    }
    return url;
}

function processJson(inputJson) {
    const processedJson = JSON.parse(JSON.stringify(inputJson));

    processedJson.versions.forEach(version => {
        if (version.downloads && version.downloads.chromedriver) {
            version.downloads.chromedriver.forEach(item => {
                item.url = convertUrl(item.url);
            });
        }
        if (version.downloads) {
            version.downloads = { chromedriver: version.downloads.chromedriver || [] };
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
            // 请求已发出，但服务器响应的状态码不在 2xx 范围内
            console.error(`HTTP error! status: ${error.response.status}`);
        } else if (error.request) {
            // 请求已发出，但没有收到响应
            console.error('No response received:', error.message);
        } else {
            // 设置请求时发生了一些问题
            console.error('Error:', error.message);
        }
        throw error;
    }
}

// 如果直接运行脚本，则执行处理
if (require.main === module) {
    fetchAndProcessJson().catch(error => {
        console.error("Script failed:", error);
        process.exit(1);
    });
}

module.exports = { processJson, fetchAndProcessJson };