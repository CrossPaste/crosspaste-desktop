#!/bin/bash

# ==============================================================================
# Configuration
# ==============================================================================

# Default path to the i18n resources directory
DEFAULT_I18N_DIR="app/src/desktopMain/resources/i18n"

# ==============================================================================
# Helper Functions
# ==============================================================================

# Display usage instructions
show_usage() {
    echo "Usage: $0 <input_file> [i18n_directory_path]"
    echo ""
    echo "Description:"
    echo "  1. Updates i18n key-value pairs from the input file."
    echo "  2. Sorts all properties files in the directory alphabetically."
    echo ""
    echo "Input file format example:"
    echo "  key=myNewKey"
    echo "  en=English Text"
    echo "  zh=Chinese Text"
    exit 1
}

# Normalize language code (e.g., convert 'zh-hant' to 'zh_hant')
normalize_lang_code() {
    local lang="$1"
    # Convert hyphen to underscore for consistency
    echo "$lang" | sed 's/-/_/g'
}

# Update or add a key-value pair in a specific properties file
update_properties_file() {
    local file_path="$1"
    local key="$2"
    local value="$3"
    local file_name=$(basename "$file_path")

    # If file doesn't exist, create it
    if [ ! -f "$file_path" ]; then
        echo "  [Create] $file_name (New file created)"
        touch "$file_path"
    fi

    # Check if key already exists
    if grep -q "^${key}=" "$file_path"; then
        # Update existing key
        # Using a temp file is safer for cross-platform compatibility (macOS/Linux)
        # Using '|' as delimiter to avoid issues if the value contains slashes
        if sed "s|^${key}=.*|${key}=${value}|" "$file_path" > "${file_path}.tmp" && mv "${file_path}.tmp" "$file_path"; then
            echo "  [Update] $file_name: $key"
            return 0
        else
            echo "  [Error] Failed to update $file_name"
            return 1
        fi
    else
        # Append new key
        if echo "${key}=${value}" >> "$file_path"; then
            echo "  [Add]    $file_name: $key"
            return 0
        else
            echo "  [Error] Failed to append to $file_name"
            return 1
        fi
    fi
}

# Sort a single properties file alphabetically
# Preserves comments and empty lines at the top of the file
sort_properties_file() {
    local file_path="$1"
    local file_name=$(basename "$file_path")
    local temp_header="${file_path}.header"
    local temp_content="${file_path}.content"

    # Step 1: Extract header (comments starting with # and empty lines at the top)
    grep -E '^[[:space:]]*#|^[[:space:]]*$' "$file_path" > "$temp_header" 2>/dev/null || true

    # Step 2: Extract content (actual key-value pairs) and sort them
    grep -v -E '^[[:space:]]*#|^[[:space:]]*$' "$file_path" | sort > "$temp_content" 2>/dev/null || true

    # Step 3: Combine header and sorted content
    if [ -s "$temp_header" ] && [ -s "$temp_content" ]; then
        # Both header and content exist
        cat "$temp_header" "$temp_content" > "${file_path}.new"
    elif [ -s "$temp_content" ]; then
        # Only content exists
        cat "$temp_content" > "${file_path}.new"
    elif [ -s "$temp_header" ]; then
        # Only header exists
        cat "$temp_header" > "${file_path}.new"
    else
        # File is empty
        touch "${file_path}.new"
    fi

    # Step 4: Overwrite the original file with the sorted version
    if mv "${file_path}.new" "$file_path"; then
        rm -f "$temp_header" "$temp_content"
        return 0
    else
        echo "  [Error] Failed to sort $file_name"
        rm -f "$temp_header" "$temp_content" "${file_path}.new"
        return 1
    fi
}

# ==============================================================================
# Main Execution Logic
# ==============================================================================

# Check parameters
if [ $# -lt 1 ]; then
    show_usage
fi

INPUT_FILE="$1"

# Use provided directory or default
if [ $# -ge 2 ]; then
    I18N_DIR="$2"
else
    I18N_DIR="$DEFAULT_I18N_DIR"
fi

# Validation: Check if input file exists
if [ ! -f "$INPUT_FILE" ]; then
    echo "Error: Input file '$INPUT_FILE' does not exist"
    exit 1
fi

# Validation: Check if i18n directory exists
if [ ! -d "$I18N_DIR" ]; then
    echo "Error: i18n directory '$I18N_DIR' does not exist"
    exit 1
fi

# Extract the key name from the input file
KEY_NAME=$(grep "^key=" "$INPUT_FILE" | cut -d'=' -f2-)

if [ -z "$KEY_NAME" ]; then
    echo "Error: No 'key' definition found in input file"
    exit 1
fi

echo "========================================="
echo "Task: Update Key '$KEY_NAME' & Sort Files"
echo "Directory: $I18N_DIR"
echo "========================================="
echo ""

# Counters
UPDATE_SUCCESS_COUNT=0
SORT_SUCCESS_COUNT=0
TOTAL_SORT_FILES=0

# ------------------------------------------------------------------------------
# Phase 1: Update Translations
# ------------------------------------------------------------------------------
echo ">>> Phase 1: Updating Translations..."

while IFS='=' read -r lang value; do
    # Skip empty lines, comments, and the 'key' definition line
    [ -z "$lang" ] && continue
    [[ "$lang" =~ ^[[:space:]]*# ]] && continue
    [ "$lang" = "key" ] && continue

    # Trim whitespace
    lang=$(echo "$lang" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
    value=$(echo "$value" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')

    # Skip if value is empty
    if [ -z "$value" ]; then
        continue
    fi

    # Normalize language code and build file path
    normalized_lang=$(normalize_lang_code "$lang")
    PROP_FILE="${I18N_DIR}/${normalized_lang}.properties"

    # Perform update
    if update_properties_file "$PROP_FILE" "$KEY_NAME" "$value"; then
        ((UPDATE_SUCCESS_COUNT++))
    fi

done < "$INPUT_FILE"

echo ""

# ------------------------------------------------------------------------------
# Phase 2: Sort All Property Files
# ------------------------------------------------------------------------------
echo ">>> Phase 2: Sorting All Property Files..."

# Loop through all .properties files in the directory
for prop_file in "$I18N_DIR"/*.properties; do
    if [ -f "$prop_file" ]; then
        ((TOTAL_SORT_FILES++))
        if sort_properties_file "$prop_file"; then
             ((SORT_SUCCESS_COUNT++))
        fi
    fi
done

echo ""
echo "========================================="
echo "Summary:"
echo "  Key Updated:       $KEY_NAME"
echo "  Languages Updated: $UPDATE_SUCCESS_COUNT"
echo "  Files Sorted:      $SORT_SUCCESS_COUNT / $TOTAL_SORT_FILES"
echo "========================================="