#!/usr/bin/env node

const fs = require('fs');

// Escape special characters in regular expressions
function escapeRegExp(string) {
    return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// Main function to process the config file
function modifyConfig(filePath, config = {}) {
    try {
        const deleteRules = config.deleteRules.map(rule => ({
            ...rule,
            regex: new RegExp(rule.pattern)
        }));

        const moveRules = config.moveRules.map(rule => ({
            ...rule,
            regex: new RegExp(rule.pattern)
        }));

        const content = fs.readFileSync(filePath, 'utf8');
        const lines = content.split('\n');

        let result = [];
        let currentSection = null;
        let inArray = false;
        let arrayIndent = 0;
        let itemsToMove = {};
        let deletedCount = 0;
        let movedCount = 0;

        // Initialize move-target sections
        moveRules.forEach(rule => {
            if (rule.to && !itemsToMove[rule.to]) {
                itemsToMove[rule.to] = [];
            }
        });

        for (let i = 0; i < lines.length; i++) {
            let line = lines[i];
            let processed = false;

            // Detect the current config section
            const sectionMatch = line.match(/^([\w\.]+)\s*=\s*(.*)$/);
            if (sectionMatch) {
                currentSection = sectionMatch[1];
                if (sectionMatch[2].includes('[')) {
                    inArray = true;
                    arrayIndent = line.indexOf('[');
                }
            }

            // Detect array start
            if (line.trim() === '[' || line.includes('= [')) {
                inArray = true;
                arrayIndent = line.indexOf('[');
            }

            // Detect array end
            if (line.includes(']')) {
                inArray = false;

                // Insert moved items before array end
                if (currentSection && itemsToMove[currentSection] && itemsToMove[currentSection].length > 0) {
                    const indent = ' '.repeat(arrayIndent + 4);

                    // Ensure previous line ends with comma if needed
                    if (result.length > 0 && !result[result.length - 1].endsWith(',') && !result[result.length - 1].includes('[')) {
                        result[result.length - 1] += ',';
                    }

                    itemsToMove[currentSection].forEach((item, index) => {
                        // Apply path replacements
                        let newPath = item;
                        config.pathReplacements.forEach(replacement => {
                            newPath = newPath.replace(replacement.from, replacement.to);
                        });

                        const isLast = index === itemsToMove[currentSection].length - 1;
                        result.push(`${indent}${newPath}${isLast ? '' : ','}`);
                    });

                    itemsToMove[currentSection] = [];
                }
            }

            // Process items inside array sections
            if (currentSection && inArray) {
                const jarMatch = line.match(/(\/[\/\w\-\.]+\.jar)/);
                if (jarMatch) {
                    const jarPath = jarMatch[1];

                    // Delete rules (highest priority)
                    for (const rule of deleteRules) {
                        if (rule.section === currentSection && rule.regex.test(jarPath)) {
                            processed = true;
                            deletedCount++;
                            console.log(`Deleted: ${jarPath} (matched pattern: ${rule.pattern})`);

                            // Handle trailing comma before array closing
                            if (i + 1 < lines.length && lines[i + 1].includes(']')) {
                                if (result.length > 0 && result[result.length - 1].endsWith(',')) {
                                    result[result.length - 1] = result[result.length - 1].slice(0, -1);
                                }
                            }
                            break;
                        }
                    }

                    // Move rules (only if not already deleted)
                    if (!processed) {
                        for (const rule of moveRules) {
                            if (rule.from === currentSection && rule.regex.test(jarPath)) {
                                if (rule.to) {
                                    itemsToMove[rule.to].push(jarPath);
                                    movedCount++;
                                    console.log(`Moved: ${jarPath} from ${rule.from} to ${rule.to}`);
                                }
                                processed = true;

                                // Handle trailing comma before array closing
                                if (i + 1 < lines.length && lines[i + 1].includes(']')) {
                                    if (result.length > 0 && result[result.length - 1].endsWith(',')) {
                                        result[result.length - 1] = result[result.length - 1].slice(0, -1);
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            }

            // If not deleted or moved, apply path replacements and add to output
            if (!processed) {
                config.pathReplacements.forEach(replacement => {
                    line = line.replace(new RegExp(escapeRegExp(replacement.from), 'g'), replacement.to);
                });
                result.push(line);
            }
        }

        // Write output file
        const outputPath = config.output || filePath;
        fs.writeFileSync(outputPath, result.join('\n'), 'utf8');

        console.log(`\n✅ Successfully modified ${filePath}`);
        if (outputPath !== filePath) {
            console.log(`📄 Output written to ${outputPath}`);
        }

        // Show statistics
        console.log(`\n📊 Statistics:`);
        console.log(`   Deleted: ${deletedCount} items`);
        console.log(`   Moved: ${movedCount} items`);

        // Warn about items that could not be moved
        Object.entries(itemsToMove).forEach(([key, value]) => {
            if (value.length > 0) {
                console.log(`⚠️  Warning: ${value.length} items for ${key} were not moved (section not found)`);
            }
        });

    } catch (error) {
        console.error('❌ Error:', error);
        process.exit(1);
    }
}

// ========== Main Program ==========
const args = process.argv.slice(2);

if (args.length === 0) {
    console.error('Usage: node rewriteConveyorConf.js <config-file> [options]');
    console.error('Options:');
    console.error('  --output <file>    Output to a different file');
    console.error('  --dry-run          Show changes without modifying files');
    process.exit(1);
}

const filePath = args[0];

// ========== Rule Definitions ==========
const config = {
    // Delete rules: match pattern and delete jar in the corresponding section
    deleteRules: [
        {
            pattern: 'javacpp-.*-android-.*.jar',
            section: 'app.inputs'
        },
        {
            pattern: 'javacpp-.*-ios-.*.jar',
            section: 'app.inputs'
        },
        {
            pattern: 'javacpp-.*-linux-arm64.jar',
            section: 'app.inputs'
        },
        {
            pattern: 'javacpp-.*-linux-riscv64.jar',
            section: 'app.inputs'
        },
        {
            pattern: 'javacpp-.*-linux-ppc64le.jar',
            section: 'app.inputs'
        },
        {
            pattern: 'leptonica-.*-android-.*.jar',
            section: 'app.inputs'
        },
        {
            pattern: 'leptonica-.*-linux-arm64.jar',
            section: 'app.inputs'
        },
        {
            pattern: 'tesseract-.*-android-.*.jar',
            section: 'app.inputs'
        },
        {
            pattern: 'tesseract-.*-linux-arm64.jar',
            section: 'app.inputs'
        },
    ],

    // Move rules: match pattern and move jar from one section to another
    moveRules: [
        {
            pattern: 'macosx-x86_64',
            from: 'app.inputs',
            to: 'app.mac.amd64.inputs'
        },
        {
            pattern: 'macosx-arm64',
            from: 'app.inputs',
            to: 'app.mac.aarch64.inputs'
        },
        {
            pattern: 'linux-x86_64',
            from: 'app.inputs',
            to: 'app.linux.amd64.glibc.inputs'
        },
        {
            pattern: 'windows-x86_64',
            from: 'app.inputs',
            to: 'app.windows.amd64.inputs'
        }
    ],

    // Path replacement rules applied to all non-deleted and non-moved items
    pathReplacements: [

    ]
};

// Parse CLI arguments
for (let i = 1; i < args.length; i++) {
    if (args[i] === '--output' && i + 1 < args.length) {
        config.output = args[++i];
    } else if (args[i] === '--dry-run') {
        config.dryRun = true;
    }
}

// If dry-run, write to a temporary file
if (config.dryRun) {
    config.output = filePath + '.tmp';
    modifyConfig(filePath, config);
    console.log('\n🔍 Dry run completed. Check', config.output);

    // Show diff info
    const original = fs.readFileSync(filePath, 'utf8');
    const modified = fs.readFileSync(config.output, 'utf8');

    if (original !== modified) {
        console.log('✏️  Changes detected');
    } else {
        console.log('✅ No changes needed');
    }

    // Remove temporary file
    fs.unlinkSync(config.output);
} else {
    modifyConfig(filePath, config);
}
