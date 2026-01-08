#!/bin/bash

# ==========================================
# CONFIGURATION
# ==========================================
# Default i18n directory
I18N_DIR="app/src/desktopMain/resources/i18n"

# Default source directories to scan for unused keys
SRC_DIRS=(
    "app/src/desktopMain/kotlin"
    "app/src/commonMain/kotlin"
    "shared/src/commonMain/kotlin"
)

# ==========================================
# USAGE
# ==========================================
show_usage() {
    echo "Usage:"
    echo "  1. Add/Update keys:  $0 <input_file> [i18n_directory_path]"
    echo "  2. Delete a key:     $0 -d <key_name> [i18n_directory_path]"
    echo "  3. Rename a key:     $0 -r <old_key> <new_key> [i18n_directory_path]"
    echo "  4. Check unused:     $0 -u [-f] [src_directory] [i18n_directory_path]"
    echo ""
    echo "Options for -u:"
    echo "  -f    Force delete unused keys from all properties files after scanning"
    echo ""
    echo "Default i18n directory: $I18N_DIR"
    exit 1
}

if [ $# -lt 1 ]; then
    show_usage
fi

# ==========================================
# MODE SELECTION & ARGUMENT PARSING
# ==========================================
MODE="add"
INPUT_FILE=""
KEY_TO_DELETE=""
OLD_KEY=""
NEW_KEY=""
CMD_SRC_DIR=""
CUSTOM_DIR=""
FORCE_DELETE=false

# Simple argument parser loop
while [[ $# -gt 0 ]]; do
    case "$1" in
        -d)
            MODE="delete"
            KEY_TO_DELETE="$2"
            shift 2
            ;;
        -r)
            MODE="rename"
            OLD_KEY="$2"
            NEW_KEY="$3"
            shift 3
            ;;
        -u)
            MODE="unused"
            shift
            if [[ "$1" == "-f" ]]; then
                FORCE_DELETE=true
                shift
            fi
            if [[ -n "$1" && ! "$1" == -* ]]; then
                CMD_SRC_DIR="$1"
                shift
            fi
            ;;
        *)
            if [[ -z "$INPUT_FILE" && "$MODE" == "add" ]]; then
                INPUT_FILE="$1"
            else
                CUSTOM_DIR="$1"
            fi
            shift
            ;;
    esac
done

if [ -n "$CUSTOM_DIR" ]; then
    I18N_DIR="$CUSTOM_DIR"
fi

if [ ! -d "$I18N_DIR" ]; then
    echo "Error: i18n directory '$I18N_DIR' does not exist"
    exit 1
fi

# ==========================================
# HELPER FUNCTIONS
# ==========================================

# Function to delete a key from all files in the i18n directory
perform_delete() {
    local target_key="$1"
    local count=0
    shopt -s nullglob
    for file in "$I18N_DIR"/*.properties; do
        if grep -q "^${target_key}=" "$file"; then
            if grep -v "^${target_key}=" "$file" > "${file}.tmp" && mv "${file}.tmp" "$file"; then
                echo "    [DELETED] $target_key from $(basename "$file")"
                ((count++))
            fi
        fi
    done
    shopt -u nullglob
    return $count
}

# ==========================================
# LOGIC: RENAME KEY (Mode: -r)
# ==========================================
if [ "$MODE" == "rename" ]; then
    if [ -z "$OLD_KEY" ] || [ -z "$NEW_KEY" ]; then
        echo "Error: Rename requires both <old_key> and <new_key>"
        exit 1
    fi

    echo "Renaming key: $OLD_KEY -> $NEW_KEY"
    echo "-----------------------------------------"

    RENAME_COUNT=0
    shopt -s nullglob
    for file in "$I18N_DIR"/*.properties; do
        if grep -q "^${OLD_KEY}=" "$file"; then
            # Extract the value of the old key
            # Using sed to extract everything after the first '='
            VALUE=$(grep "^${OLD_KEY}=" "$file" | sed "s/^${OLD_KEY}=//")

            # Remove the old key line and save to temp
            grep -v "^${OLD_KEY}=" "$file" > "${file}.tmp"

            # Append the new key with the original value
            echo "${NEW_KEY}=${VALUE}" >> "${file}.tmp"

            # Sort the file (maintains consistency with 'add' logic)
            sort "${file}.tmp" -o "$file"
            rm -f "${file}.tmp"

            echo "  [RENAMED] In $(basename "$file")"
            ((RENAME_COUNT++))
        fi
    done
    shopt -u nullglob

    echo "-----------------------------------------"
    echo "Complete. Renamed in $RENAME_COUNT files."
    exit 0
fi

# ==========================================
# LOGIC: UNUSED KEYS CHECK (Mode: -u)
# ==========================================
if [ "$MODE" == "unused" ]; then
    ZH_FILE="${I18N_DIR}/zh.properties"
    if [ ! -f "$ZH_FILE" ]; then
        echo "Error: Reference file $ZH_FILE not found."
        exit 1
    fi

    SCAN_TARGETS=()
    if [ -n "$CMD_SRC_DIR" ]; then
        SCAN_TARGETS=("$CMD_SRC_DIR")
    else
        SCAN_TARGETS=("${SRC_DIRS[@]}")
    fi

    echo ""
    echo "Scanning for unused keys..."
    echo "Target Directories: ${SCAN_TARGETS[*]}"
    [ "$FORCE_DELETE" = true ] && echo "Mode: SCAN & AUTO-DELETE (-f enabled)" || echo "Mode: SCAN ONLY"
    echo "-----------------------------------------"

    UNUSED_KEYS=()
    TOTAL_KEYS=0

    # Extract all keys from zh.properties
    ALL_KEYS=$(grep "^[^#[:space:]]" "$ZH_FILE" | cut -d'=' -f1)

    while read -r key; do
        [ -z "$key" ] && continue
        ((TOTAL_KEYS++))

        # Handle numeric suffix (e.g., guide_0, guide_1 -> search for "guide")
        SEARCH_PATTERN="$key"
        if [[ "$key" =~ _[0-9]+$ ]]; then
            SEARCH_PATTERN=$(echo "$key" | sed -E 's/_[0-9]+$//')
            # English Note: Strip trailing numbers to support dynamic key construction in code
        fi

        FOUND=false
        for dir in "${SCAN_TARGETS[@]}"; do
            if [ -d "$dir" ]; then
                # Search for pattern inside double quotes
                if grep -rq "\"$SEARCH_PATTERN" "$dir"; then
                    FOUND=true
                    break
                fi
            fi
        done

        if [ "$FOUND" = false ]; then
            echo "  [UNUSED] $key (Searched: \"$SEARCH_PATTERN\")"
            UNUSED_KEYS+=("$key")
        fi
    done <<< "$ALL_KEYS"

    echo "-----------------------------------------"
    echo "Scan complete. Found ${#UNUSED_KEYS[@]} unused keys."

    if [ "$FORCE_DELETE" = true ] && [ ${#UNUSED_KEYS[@]} -gt 0 ]; then
        echo "Starting auto-deletion..."
        for uk in "${UNUSED_KEYS[@]}"; do
            perform_delete "$uk"
        done
        echo "All unused keys have been removed."
    elif [ ${#UNUSED_KEYS[@]} -gt 0 ]; then
        echo "Tip: Run with '-u -f' to automatically remove these keys."
    fi
    exit 0
fi

# ==========================================
# LOGIC: INDIVIDUAL DELETE (Mode: -d)
# ==========================================
if [ "$MODE" == "delete" ]; then
    echo "Deleting key: $KEY_TO_DELETE"
    perform_delete "$KEY_TO_DELETE"
    exit 0
fi

# ==========================================
# LOGIC: ADD/UPDATE KEY (Default Mode)
# ==========================================
if [ ! -f "$INPUT_FILE" ]; then
    echo "Error: Input file '$INPUT_FILE' does not exist"
    exit 1
fi

KEY_NAME=$(grep "^key=" "$INPUT_FILE" | cut -d'=' -f2-)
if [ -z "$KEY_NAME" ]; then
    echo "Error: No 'key=' definition found in input file"
    exit 1
fi

echo ""
echo "Preparing to add/update key: $KEY_NAME"
echo "i18n directory: $I18N_DIR"
echo "-----------------------------------------"

SUCCESS_COUNT=0
TOTAL_COUNT=0
SKIPPED_COUNT=0

while IFS='=' read -r lang value; do
    [ -z "$lang" ] && continue
    [[ "$lang" =~ ^[[:space:]]*# ]] && continue

    # Trim whitespace
    lang=$(echo "$lang" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
    value=$(echo "$value" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')

    [ "$lang" = "key" ] && continue
    if [ -z "$value" ]; then
        ((SKIPPED_COUNT++))
        continue
    fi

    NORM_LANG=$(echo "$lang" | sed 's/-/_/g')
    PROP_FILE="${I18N_DIR}/${NORM_LANG}.properties"
    FILE_NAME=$(basename "$PROP_FILE")

    [ ! -f "$PROP_FILE" ] && touch "$PROP_FILE"

    # Restoration of detailed logging
    if grep -q "^${KEY_NAME}=" "$PROP_FILE"; then
        # UPDATE Logic
        if sed "s|^${KEY_NAME}=.*|${KEY_NAME}=${value}|" "$PROP_FILE" > "${PROP_FILE}.tmp" && mv "${PROP_FILE}.tmp" "$PROP_FILE"; then
            echo "  Updated (In-Place): $FILE_NAME"
            ((SUCCESS_COUNT++))
        fi
    else
        # ADD Logic
        if echo "${KEY_NAME}=${value}" >> "$PROP_FILE"; then
            sort "$PROP_FILE" -o "$PROP_FILE"
            echo "  Added & Sorted:    $FILE_NAME"
            ((SUCCESS_COUNT++))
        fi
    fi
    ((TOTAL_COUNT++))
done < "$INPUT_FILE"

echo "-----------------------------------------"
echo "Summary: Processed $TOTAL_COUNT, Updated $SUCCESS_COUNT, Skipped $SKIPPED_COUNT."