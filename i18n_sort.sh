#!/bin/bash

# Default i18n directory path
I18N_DIR="app/src/desktopMain/resources/i18n"

# Show usage instructions
show_usage() {
    echo "Usage: $0 [i18n_directory_path]"
    echo ""
    echo "This script sorts all key-value pairs in properties files alphabetically."
    echo "Empty lines and comments are preserved at the top of the file."
    exit 1
}

# Check parameters
if [ $# -ge 1 ]; then
    if [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
        show_usage
    fi
    I18N_DIR="$1"
fi

# Check if i18n directory exists
if [ ! -d "$I18N_DIR" ]; then
    echo "Error: i18n directory '$I18N_DIR' does not exist"
    exit 1
fi

# Sort a single properties file
sort_properties_file() {
    local file_path="$1"
    local file_name=$(basename "$file_path")
    local temp_file="${file_path}.tmp"
    local temp_sorted="${file_path}.sorted"

    echo "Sorting $file_name..."

    # Separate comments/empty lines and key-value pairs
    # Comments and empty lines go to the top
    grep -E '^[[:space:]]*#|^[[:space:]]*$' "$file_path" > "$temp_file" 2>/dev/null || true

    # Extract and sort key-value pairs
    grep -v -E '^[[:space:]]*#|^[[:space:]]*$' "$file_path" | sort > "$temp_sorted" 2>/dev/null || true

    # Combine comments/empty lines with sorted key-value pairs
    if [ -s "$temp_file" ] && [ -s "$temp_sorted" ]; then
        # Both files have content
        cat "$temp_file" "$temp_sorted" > "${file_path}.new"
    elif [ -s "$temp_sorted" ]; then
        # Only sorted content exists
        cat "$temp_sorted" > "${file_path}.new"
    elif [ -s "$temp_file" ]; then
        # Only comments/empty lines exist
        cat "$temp_file" > "${file_path}.new"
    else
        # File is empty
        touch "${file_path}.new"
    fi

    # Replace original file
    if mv "${file_path}.new" "$file_path"; then
        echo "  Successfully sorted $file_name"
        rm -f "$temp_file" "$temp_sorted"
        return 0
    else
        echo "  Error: Failed to sort $file_name"
        rm -f "$temp_file" "$temp_sorted" "${file_path}.new"
        return 1
    fi
}

# Process counter
SUCCESS_COUNT=0
TOTAL_COUNT=0

# Find and sort all properties files
echo "Sorting properties files in: $I18N_DIR"
echo ""

for prop_file in "$I18N_DIR"/*.properties; do
    if [ -f "$prop_file" ]; then
        ((TOTAL_COUNT++))
        if sort_properties_file "$prop_file"; then
            ((SUCCESS_COUNT++))
        fi
    fi
done

echo ""
echo "Done! Successfully sorted $SUCCESS_COUNT/$TOTAL_COUNT files"