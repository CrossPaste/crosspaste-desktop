#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

/**
 * Process JAR files listed in a text file based on regex matching
 * @param {string} regexPattern - Regular expression pattern to match lines
 * @param {string} filePath - Path to text file containing JAR paths
 */
function processJarFilesFromList(regexPattern, filePath) {
    // Validate inputs
    if (!fs.existsSync(filePath)) {
        throw new Error(`Text file not found: ${filePath}`);
    }

    // Read and parse the text file
    console.log(`Reading file: ${filePath}`);
    const fileContent = fs.readFileSync(filePath, 'utf8');
    const lines = fileContent.split('\n').filter(line => line.trim() !== '');

    if (lines.length === 0) {
        console.log('No lines found in the file');
        return;
    }

    console.log(`Found ${lines.length} lines in file`);
    console.log(`Using regex pattern: ${regexPattern}`);

    // Compile regex
    let regex;
    try {
        regex = new RegExp(regexPattern);
    } catch (error) {
        throw new Error(`Invalid regex pattern: ${error.message}`);
    }

    // Process each line
    const jarPaths = [];
    lines.forEach((line, index) => {
        const trimmedLine = line.trim();
        if (regex.test(trimmedLine)) {
            // Extract JAR path by removing surrounding text (if any)
            // This implementation assumes the entire line is the path after matching
            // You can modify this logic based on your specific needs
            const jarPath = extractJarPath(trimmedLine, regex);
            if (jarPath && fs.existsSync(jarPath)) {
                jarPaths.push(jarPath);
                console.log(`Line ${index + 1}: MATCH - ${jarPath}`);
            } else {
                console.log(`Line ${index + 1}: MATCH but invalid path - ${trimmedLine}`);
            }
        } else {
            console.log(`Line ${index + 1}: NO MATCH - ${trimmedLine}`);
        }
    });

    if (jarPaths.length === 0) {
        console.log('No valid JAR paths found after regex matching');
        return;
    }

    console.log(`\nFound ${jarPaths.length} JAR files to process`);
    console.log('='.repeat(60));

    // Process each JAR file
    jarPaths.forEach((jarPath, index) => {
        console.log(`\nProcessing JAR ${index + 1}/${jarPaths.length}: ${jarPath}`);
        console.log('-'.repeat(40));

        try {
            removeTesseractFilesFromJar(jarPath);
            console.log(`✓ Successfully processed: ${jarPath}`);
        } catch (error) {
            console.log(`✗ Failed to process: ${jarPath}`);
            console.log(`  Error: ${error.message}`);
        }
    });

    console.log('='.repeat(60));
    console.log(`Processing completed! ${jarPaths.length} JAR files processed.`);
}

/**
 * Extract JAR path from matched line
 * This implementation can be customized based on your specific text format
 * @param {string} line - The matched line
 * @param {RegExp} regex - The regex used for matching
 * @returns {string} - Extracted JAR path
 */
function extractJarPath(line, regex) {
    // Default implementation: use the entire line as the path
    // You can modify this to extract specific parts using regex groups
    return line.trim();
}

/**
 * Remove exact "tesseract" files from JAR (files only, keep folders with same name)
 * @param {string} jarPath - Path to JAR file
 */
function removeTesseractFilesFromJar(jarPath) {
    if (!fs.existsSync(jarPath)) {
        throw new Error(`JAR file not found: ${jarPath}`);
    }

    if (!jarPath.toLowerCase().endsWith('.jar')) {
        throw new Error(`Not a JAR file: ${jarPath}`);
    }

    // Create temp directory
    const tempDir = fs.mkdtempSync(path.join(require('os').tmpdir(), 'jar-process-'));

    try {
        // 1. Extract JAR to temp directory
        console.log('  Extracting JAR file...');
        execSync(`unzip -q "${jarPath}" -d "${tempDir}"`, { stdio: 'inherit' });

        // 2. Find and remove exact "tesseract" files (files only, not folders)
        console.log('  Searching for tesseract files...');
        const filesToRemove = findExactTesseractFiles(tempDir);

        if (filesToRemove.length === 0) {
            console.log('  No tesseract files found');
            return;
        }

        console.log(`  Found ${filesToRemove.length} tesseract files to remove:`);
        filesToRemove.forEach(file => console.log(`    - ${path.relative(tempDir, file)}`));

        // 3. Remove files
        console.log('  Removing tesseract files...');
        filesToRemove.forEach(file => {
            fs.unlinkSync(file);
        });

        // 4. Create new JAR using zip
        console.log('  Creating new JAR...');
        const tempJarPath = path.join(tempDir, 'new.jar');

        // Change to temp directory and zip all contents
        const originalDir = process.cwd();
        process.chdir(tempDir);
        execSync(`zip -rq "${tempJarPath}" . -x "new.jar"`, { stdio: 'inherit' });
        process.chdir(originalDir);

        // 5. Backup original and replace
        console.log('  Replacing original JAR file...');
        const backupPath = `${jarPath}.backup`;
        fs.copyFileSync(jarPath, backupPath);

        fs.copyFileSync(tempJarPath, jarPath);

    } catch (error) {
        console.error('  Error during processing:', error.message);
        throw error;
    } finally {
        // 6. Cleanup temp directory
        console.log('  Cleaning up temp files...');
        try {
            fs.rmSync(tempDir, { recursive: true, force: true });
        } catch (cleanupError) {
            console.warn('  Warning during cleanup:', cleanupError.message);
        }
    }
}

/**
 * Find files with exact name "tesseract" (case-sensitive)
 * @param {string} dir - Directory to search
 * @returns {string[]} - Array of file paths
 */
function findExactTesseractFiles(dir) {
    const results = [];

    function search(currentPath) {
        const items = fs.readdirSync(currentPath);

        for (const item of items) {
            const fullPath = path.join(currentPath, item);
            const stat = fs.statSync(fullPath);

            if (stat.isDirectory()) {
                // Recursively search directories
                search(fullPath);
            } else if (stat.isFile() && item === 'tesseract') {
                // Exact match for file named "tesseract"
                results.push(fullPath);
            }
        }
    }

    search(dir);
    return results;
}

/**
 * Advanced JAR path extraction with regex groups support
 * Use this if you need to extract specific parts from the matched line
 */
function extractJarPathAdvanced(line, regex) {
    const match = line.match(regex);
    if (!match) return null;

    // If regex has capture groups, use the first capture group as path
    // Otherwise use the entire matched string
    if (match.length > 1 && match[1]) {
        return match[1].trim();
    } else {
        return match[0].trim();
    }
}

const args = process.argv.slice(2);

if (args.length < 2) {
    console.log('Usage: node processJarFiles.js "<regex-pattern>" <file-path>');
    console.log('Examples:');
    console.log('  node processJarFiles.js "\\.jar$" jars.txt');
    console.log('  node processJarFiles.js "^/path/.*\\.jar" file_list.txt');
    console.log('  node processJarFiles.js ".*/build/.*\\.jar" paths.txt');
    process.exit(1);
}

const regexPattern = args[0];
const filePath = path.resolve(args[1]);

console.log('JAR File Processor');
console.log('='.repeat(60));
console.log(`Regex Pattern: ${regexPattern}`);
console.log(`Input File: ${filePath}`);
console.log('='.repeat(60));

try {
    processJarFilesFromList(regexPattern, filePath);
} catch (error) {
    console.error('Processing failed:', error.message);
    process.exit(1);
}
