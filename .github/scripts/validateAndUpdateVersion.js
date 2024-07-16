const semver = require('semver');
const fs = require('fs-extra');
const path = require('path');

async function validateAndUpdateVersion() {
    // Retrieve the Git tag
    const tag = process.env.GITHUB_REF.replace('refs/tags/', '');
    const isPreRelease = process.env.PRE_RELEASE.toLowerCase() === 'true'
    if (!semver.valid(tag)) {
        console.log(`Invalid version tag: ${tag}`);
        process.exit(1);
    }

    let currentVersion = semver.parse(tag);

    // Read the properties file
    const propertiesPath = path.join(__dirname, '../../composeApp/src/desktopMain/resources/crosspaste-version.properties');
    console.log('Reading properties file:', propertiesPath);
    let propertiesContent = await fs.readFile(propertiesPath, 'utf8');
    const propertiesVersionMatch = propertiesContent.match(/version=(.*)$/);
    if (!propertiesVersionMatch) {
        console.log('Version not found in properties file.');
        process.exit(1);
    }

    const propertiesVersion = propertiesVersionMatch[1];

    if (!semver.valid(propertiesVersion)) {
        console.log(`Invalid propertiesVersion: ${propertiesVersion}.`);
        process.exit(1);
    }

    let currentPropertiesVersion = semver.parse(propertiesVersion);

    // Compare major, minor, and patch parts of the version
    if (currentVersion.major === currentPropertiesVersion.major &&
        currentVersion.minor === currentPropertiesVersion.minor &&
        currentVersion.patch === currentPropertiesVersion.patch) {
        console.log('Version match successful.');

        // Parse the tag to extract pre-release information
        const version = `${semver.major(tag)}.${semver.minor(tag)}.${semver.patch(tag)}`;
        const envPath = process.env.GITHUB_ENV || './.env';
        if (isPreRelease) {
            if (currentVersion.prerelease.length === 0) {
                console.log('Pre-release version not found in tag.');
                process.exit(1);
            }
            const preReleaseVersion = currentVersion.prerelease.join('.');
            // Append pre-release information to the properties file
            propertiesContent += `\nprerelease=${preReleaseVersion}`;
            await fs.writeFile(propertiesPath, propertiesContent, 'utf8');
            console.log('Updated properties file with pre-release version:', preReleaseVersion);
            fs.appendFileSync(envPath, `PRE_RELEASE_VERSION=${preReleaseVersion}\n`);
        } else {
            if (currentVersion.prerelease.length > 0) {
                console.log('Pre-release version found in tag.');
                process.exit(1);
            }
        }
        fs.appendFileSync(envPath, `VERSION=${version}\n`);
    } else {
        console.log('Version mismatch.');
        process.exit(1);
    }
}

validateAndUpdateVersion().catch(err => {
    console.error('Error during version validation and update:', err);
    process.exit(1);
});
