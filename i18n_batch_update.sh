#!/bin/bash

# Default i18n directory path
I18N_DIR="app/src/desktopMain/resources/i18n"

# Show usage instructions
show_usage() {
    echo "Usage: $0 <input_file> [i18n_directory_path]"
    echo ""
    echo "Input file format example:"
    echo "key=myNewKey"
    echo "de=Deutscher Text"
    echo "en=English Text"
    echo "es=Texto en español"
    echo "zh=中文文本"
    echo "..."
    exit 1
}

# Check parameters
if [ $# -lt 1 ]; then
    show_usage
fi

INPUT_FILE="$1"

# If second parameter is provided, use it as i18n directory
if [ $# -ge 2 ]; then
    I18N_DIR="$2"
fi

# Check if input file exists
if [ ! -f "$INPUT_FILE" ]; then
    echo "Error: Input file '$INPUT_FILE' does not exist"
    exit 1
fi

# Check if i18n directory exists
if [ ! -d "$I18N_DIR" ]; then
    echo "Error: i18n directory '$I18N_DIR' does not exist"
    exit 1
fi

# Read key name
KEY_NAME=$(grep "^key=" "$INPUT_FILE" | cut -d'=' -f2-)

if [ -z "$KEY_NAME" ]; then
    echo "Error: No 'key' definition found in input file"
    exit 1
fi

echo ""
echo "Preparing to add key: $KEY_NAME"
echo ""

# Process counter
SUCCESS_COUNT=0
TOTAL_COUNT=0

# Add or update key-value pair in properties file
update_properties_file() {
    local file_path="$1"
    local key="$2"
    local value="$3"
    local file_name=$(basename "$file_path")

    echo "Processing $file_name:"

    # If file doesn't exist, create it
    if [ ! -f "$file_path" ]; then
        echo "  Warning: File $file_path does not exist, creating new file"
        touch "$file_path"
    fi

    # Check if key already exists
    if grep -q "^${key}=" "$file_path"; then
        # Update existing key
        # Use temp file to avoid issues on some systems
        sed "s|^${key}=.*|${key}=${value}|" "$file_path" > "${file_path}.tmp" && mv "${file_path}.tmp" "$file_path"
        echo "  Updated existing key: $key"
    else
        # Add new key
        echo "${key}=${value}" >> "$file_path"
        echo "  Added new key: ${key}=${value}"
    fi

    if [ $? -eq 0 ]; then
        ((SUCCESS_COUNT++))
        return 0
    else
        echo "  Error: Failed to update file"
        return 1
    fi
}

# Read input file and process each language
while IFS='=' read -r lang value; do
    # Skip empty lines and comments
    [ -z "$lang" ] && continue
    [[ "$lang" =~ ^[[:space:]]*# ]] && continue

    # Trim whitespace
    lang=$(echo "$lang" | tr -d '[:space:]')
    value=$(echo "$value" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')

    # Skip key line
    [ "$lang" = "key" ] && continue

    # Build file path
    PROP_FILE="${I18N_DIR}/${lang}.properties"

    # Update file
    update_properties_file "$PROP_FILE" "$KEY_NAME" "$value"
    ((TOTAL_COUNT++))

done < "$INPUT_FILE"

echo ""
echo "Done! Successfully updated $SUCCESS_COUNT/$TOTAL_COUNT files"