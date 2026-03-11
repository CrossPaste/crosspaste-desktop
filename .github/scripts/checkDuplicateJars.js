#!/usr/bin/env node

/**
 * Check for duplicate JARs (same artifact, different versions) in a Conveyor config file.
 *
 * Scans all `app.inputs` / `app.*.inputs` sections for JAR entries, groups them by
 * artifact name, and reports any artifact that appears with more than one version.
 *
 * Exit code 0: no duplicates found.
 * Exit code 1: duplicates detected (prints detailed warnings to stderr).
 */

const fs = require('fs');

// Compare two version strings numerically (e.g. "1.10.0" vs "1.7.3")
function compareVersions(a, b) {
    const partsA = a.split('.').map(Number);
    const partsB = b.split('.').map(Number);
    const len = Math.max(partsA.length, partsB.length);
    for (let i = 0; i < len; i++) {
        const numA = partsA[i] || 0;
        const numB = partsB[i] || 0;
        if (numA !== numB) return numA - numB;
    }
    return 0;
}

// Parse JAR filename into { artifact, version }
// e.g. "kotlinx-serialization-core-jvm-1.10.0.jar" -> { artifact: "kotlinx-serialization-core-jvm", version: "1.10.0" }
function parseJarFileName(jarPath) {
    const fileName = jarPath.split('/').pop();
    const match = fileName.match(/^(.+?)-(\d+\..+?)\.jar$/);
    if (match) {
        return { artifact: match[1], version: match[2] };
    }
    return null;
}

// Extract Maven group:artifact from the Gradle cache path
// e.g. ".../files-2.1/org.jetbrains.kotlinx/kotlinx-serialization-core-jvm/1.10.0/..." -> "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm"
function parseMavenCoordinate(jarPath) {
    const match = jarPath.match(/files-2\.1\/([^/]+)\/([^/]+)\/([^/]+)\//);
    if (match) {
        return { group: match[1], artifact: match[2], version: match[3] };
    }
    return null;
}

function checkDuplicates(filePath) {
    const content = fs.readFileSync(filePath, 'utf8');
    const lines = content.split('\n');

    // Collect all JAR entries: Map<"group:artifact", [{ version, jarPath, section }]>
    const artifacts = {};
    let currentSection = null;
    let inArray = false;

    for (const line of lines) {
        const sectionMatch = line.match(/^([\w.]+)\s*=\s*(.*)/);
        if (sectionMatch) {
            currentSection = sectionMatch[1];
            if (sectionMatch[2].includes('[')) inArray = true;
        }
        if (line.trim() === '[' || line.includes('= [')) inArray = true;
        if (line.includes(']')) {
            inArray = false;
            continue;
        }

        if (!inArray) continue;

        const jarMatch = line.match(/(\/[/\w\-.]+\.jar)/);
        if (!jarMatch) continue;

        const jarPath = jarMatch[1];

        // Try Maven coordinate from Gradle cache path first (most accurate)
        const maven = parseMavenCoordinate(jarPath);
        if (maven) {
            const key = `${maven.group}:${maven.artifact}`;
            if (!artifacts[key]) artifacts[key] = [];
            artifacts[key].push({
                version: maven.version,
                jarPath,
                section: currentSection,
            });
            continue;
        }

        // Fallback: parse from filename
        const parsed = parseJarFileName(jarPath);
        if (parsed) {
            const key = parsed.artifact;
            if (!artifacts[key]) artifacts[key] = [];
            artifacts[key].push({
                version: parsed.version,
                jarPath,
                section: currentSection,
            });
        }
    }

    // Find duplicates: same artifact with different versions
    const duplicates = [];
    for (const [key, entries] of Object.entries(artifacts)) {
        const uniqueVersions = [...new Set(entries.map((e) => e.version))];
        if (uniqueVersions.length > 1) {
            uniqueVersions.sort(compareVersions);
            duplicates.push({ key, entries, versions: uniqueVersions });
        }
    }

    return duplicates;
}

// ========== Main ==========
const args = process.argv.slice(2);
if (args.length === 0) {
    console.error('Usage: node checkDuplicateJars.js <conveyor-config-file>');
    process.exit(1);
}

const filePath = args[0];
if (!fs.existsSync(filePath)) {
    console.error(`File not found: ${filePath}`);
    process.exit(1);
}

const duplicates = checkDuplicates(filePath);

if (duplicates.length === 0) {
    console.log('✅ No duplicate JAR versions found in ' + filePath);
    process.exit(0);
}

// Print warnings
console.error('');
console.error('⚠️  WARNING: Duplicate JARs with different versions detected!');
console.error('   This can cause AbstractMethodError / NoSuchMethodError at runtime.');
console.error('   File: ' + filePath);
console.error('');

for (const dup of duplicates) {
    console.error(`  ❌ ${dup.key}`);
    for (const entry of dup.entries) {
        console.error(`     - ${entry.version} (${entry.section})`);
    }
}

console.error('');
console.error(
    `Found ${duplicates.length} artifact(s) with version conflicts. ` +
        'Fix dependency resolution in build.gradle.kts before packaging.',
);
console.error('');

process.exit(1);
