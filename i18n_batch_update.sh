#!/bin/bash

# ==========================================
# CONFIGURATION
# ==========================================
# Default i18n directory (restored to your specified path)
I18N_DIR="app/src/desktopMain/resources/i18n"

# ==========================================
# USAGE
# ==========================================
show_usage() {
    echo "Usage:"
    echo "  1. Add/Update keys:  $0 <input_file> [i18n_directory_path]"
    echo "  2. Delete a key:     $0 -d <key_name> [i18n_directory_path]"
    echo ""
    echo "Default directory: $I18N_DIR"
    exit 1
}

# Check parameters
if [ $# -lt 1 ]; then
    show_usage
fi

# ==========================================
# MODE SELECTION & ARGUMENT PARSING
# ==========================================

MODE="add"
INPUT_FILE=""
KEY_TO_DELETE=""
CUSTOM_DIR=""

if [ "$1" == "-d" ]; then
    # --- DELETE MODE ---
    MODE="delete"
    KEY_TO_DELETE="$2"
    if [ -z "$KEY_TO_DELETE" ]; then
        echo "Error: Key name is required for delete mode."
        exit 1
    fi
    # If 3rd parameter exists, it's the custom directory
    if [ -n "$3" ]; then
        CUSTOM_DIR="$3"
    fi
else
    # --- ADD MODE ---
    MODE="add"
    INPUT_FILE="$1"
    # If 2nd parameter exists, it's the custom directory
    if [ -n "$2" ]; then
        CUSTOM_DIR="$2"
    fi
fi

# Apply custom directory if provided
if [ -n "$CUSTOM_DIR" ]; then
    I18N_DIR="$CUSTOM_DIR"
fi

# Check if i18n directory exists
if [ ! -d "$I18N_DIR" ]; then
    echo "Error: i18n directory '$I18N_DIR' does not exist"
    exit 1
fi

# ==========================================
# LOGIC: DELETE KEY
# ==========================================
if [ "$MODE" == "delete" ]; then
    echo ""
    echo "Target Key: $KEY_TO_DELETE"
    echo "Directory:  $I18N_DIR"
    echo "-----------------------------------------"

    DELETED_COUNT=0

    shopt -s nullglob
    for file in "$I18N_DIR"/*.properties; do
        filename=$(basename "$file")

        # Check if key exists
        if grep -q "^${KEY_TO_DELETE}=" "$file"; then
            # Delete line using grep -v (Preserves order of other keys)
            if grep -v "^${KEY_TO_DELETE}=" "$file" > "${file}.tmp" && mv "${file}.tmp" "$file"; then
                echo "  [DELETED] Removed from: $filename"
                ((DELETED_COUNT++))
            else
                echo "  [ERROR] Failed to update: $filename"
                rm -f "${file}.tmp"
            fi
        fi
    done
    shopt -u nullglob

    echo "-----------------------------------------"
    echo "Done. Removed key from $DELETED_COUNT files."
    exit 0
fi

# ==========================================
# LOGIC: ADD/UPDATE KEY
# ==========================================

# Check if input file exists
if [ ! -f "$INPUT_FILE" ]; then
    echo "Error: Input file '$INPUT_FILE' does not exist"
    exit 1
fi

# Read key name
KEY_NAME=$(grep "^key=" "$INPUT_FILE" | cut -d'=' -f2-)

if [ -z "$KEY_NAME" ]; then
    echo "Error: No 'key' definition found in input file"
    exit 1
fi

echo ""
echo "Preparing to add/update key: $KEY_NAME"
echo "i18n directory: $I18N_DIR"
echo ""

SUCCESS_COUNT=0
TOTAL_COUNT=0
SKIPPED_COUNT=0

# Function to update property file
update_properties_file() {
    local file_path="$1"
    local key="$2"
    local value="$3"
    local file_name=$(basename "$file_path")

    # Create file if missing
    if [ ! -f "$file_path" ]; then
        echo "  Warning: Creating new file $file_name"
        touch "$file_path"
    fi

    # 1. Check if Key exists (UPDATE Logic)
    if grep -q "^${key}=" "$file_path"; then
        # If Key exists, use sed for in-place replacement
        # This preserves the original line number and position, keeping file order intact
        if sed "s|^${key}=.*|${key}=${value}|" "$file_path" > "${file_path}.tmp" && mv "${file_path}.tmp" "$file_path"; then
            echo "  Updated (In-Place): $file_name"
            ((SUCCESS_COUNT++))
        fi
    else
        # 2. If Key does not exist (ADD Logic)
        # Append to the end of the file first
        if echo "${key}=${value}" >> "$file_path"; then
            # [Critical Change] After appending, sort the file content
            # This ensures the new Key is placed in alphabetical order
            sort "$file_path" -o "$file_path"
            echo "  Added & Sorted: $file_name"
            ((SUCCESS_COUNT++))
        fi
    fi
}

normalize_lang_code() {
    echo "$1" | sed 's/-/_/g'
}

# Process input file
while IFS='=' read -r lang value; do
    [ -z "$lang" ] && continue
    [[ "$lang" =~ ^[[:space:]]*# ]] && continue

    lang=$(echo "$lang" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
    value=$(echo "$value" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')

    [ "$lang" = "key" ] && continue

    if [ -z "$value" ]; then
        ((SKIPPED_COUNT++))
        continue
    fi

    normalized_lang=$(normalize_lang_code "$lang")
    PROP_FILE="${I18N_DIR}/${normalized_lang}.properties"

    update_properties_file "$PROP_FILE" "$KEY_NAME" "$value"
    ((TOTAL_COUNT++))

done < "$INPUT_FILE"

echo ""
echo "Summary: Processed $TOTAL_COUNT, Updated $SUCCESS_COUNT."