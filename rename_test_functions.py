#!/usr/bin/env python3
"""
Automated script to rename Kotlin test functions with spaces to camelCase.
Preserves readability by adding @DisplayName annotations.

Usage:
    python3 rename_test_functions.py [--dry-run] [--backup]

Options:
    --dry-run    Show what would be changed without modifying files
    --backup     Create backup files before modifying
"""

import re
import os
import sys
import argparse
from pathlib import Path
from typing import List, Tuple, Dict

# Test directory to process
TEST_DIR = Path("app/src/androidTest")

def to_camel_case(text: str) -> str:
    """Convert a string to camelCase."""
    # Remove special characters and split by spaces/underscores
    words = re.split(r'[\s_]+', text)
    # First word lowercase, rest capitalized
    return words[0].lower() + ''.join(word.capitalize() for word in words[1:] if word)

def find_test_functions(content: str) -> List[Tuple[str, str, int]]:
    """
    Find all test functions with backticks.
    Returns list of (original_name, camel_case_name, line_number)
    """
    functions = []
    # Pattern to match: @Test\n    fun `function name`()
    pattern = r'@Test\s+fun\s+`([^`]+)`\s*\(\)'
    
    for match in re.finditer(pattern, content, re.MULTILINE):
        original_name = match.group(1)
        camel_case_name = to_camel_case(original_name)
        line_number = content[:match.start()].count('\n') + 1
        functions.append((original_name, camel_case_name, line_number))
    
    return functions

def rename_function_in_content(content: str, original_name: str, camel_case_name: str) -> str:
    """Rename a function and add a comment with the original readable name."""
    # Pattern to match the function definition
    pattern = rf'(@Test\s+)(fun\s+`{re.escape(original_name)}`\s*\(\))'
    
    def replace_func(match):
        test_annotation = match.group(1)
        # Add comment with original readable name (JUnit 4 compatible)
        comment = f'    // Test: {original_name}\n    {test_annotation}'
        # Replace function name
        func_def = match.group(2).replace(f'`{original_name}`', camel_case_name)
        return comment + func_def
    
    return re.sub(pattern, replace_func, content, flags=re.MULTILINE)

def process_file(file_path: Path, dry_run: bool = False, backup: bool = False) -> Dict[str, any]:
    """Process a single Kotlin test file."""
    result = {
        'file': str(file_path),
        'functions_renamed': [],
        'modified': False,
        'error': None
    }
    
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Find all test functions with backticks
        functions = find_test_functions(content)
        
        if not functions:
            return result
        
        # Create backup if requested
        if backup and not dry_run:
            backup_path = file_path.with_suffix(file_path.suffix + '.bak')
            with open(backup_path, 'w', encoding='utf-8') as f:
                f.write(content)
            result['backup'] = str(backup_path)
        
        # Rename functions (in reverse order to preserve positions)
        new_content = content
        for original_name, camel_case_name, line_num in reversed(functions):
            new_content = rename_function_in_content(new_content, original_name, camel_case_name)
            result['functions_renamed'].append({
                'original': original_name,
                'camel_case': camel_case_name,
                'line': line_num
            })
        
        # Write modified content if not dry run
        if new_content != content:
            result['modified'] = True
            if not dry_run:
                with open(file_path, 'w', encoding='utf-8') as f:
                    f.write(new_content)
        
    except Exception as e:
        result['error'] = str(e)
    
    return result

def main():
    parser = argparse.ArgumentParser(
        description='Rename Kotlin test functions with spaces to camelCase'
    )
    parser.add_argument('--dry-run', action='store_true',
                       help='Show what would be changed without modifying files')
    parser.add_argument('--backup', action='store_true',
                       help='Create backup files before modifying')
    args = parser.parse_args()
    
    if not TEST_DIR.exists():
        print(f"Error: Test directory not found: {TEST_DIR}")
        sys.exit(1)
    
    # Find all Kotlin test files
    test_files = list(TEST_DIR.rglob("*.kt"))
    
    if not test_files:
        print(f"No Kotlin test files found in {TEST_DIR}")
        sys.exit(0)
    
    print(f"Found {len(test_files)} test file(s) to process\n")
    
    total_renamed = 0
    total_modified = 0
    
    for file_path in test_files:
        result = process_file(file_path, dry_run=args.dry_run, backup=args.backup)
        
        if result['error']:
            print(f"❌ Error processing {result['file']}: {result['error']}")
            continue
        
        if result['functions_renamed']:
            total_renamed += len(result['functions_renamed'])
            if result['modified']:
                total_modified += 1
            
            status = "🔍 [DRY RUN]" if args.dry_run else "✅"
            print(f"{status} {result['file']}")
            
            if 'backup' in result:
                print(f"   📦 Backup: {result['backup']}")
            
            for func in result['functions_renamed']:
                print(f"   • {func['original']}")
                print(f"     → {func['camel_case']} (line {func['line']})")
            print()
    
    print(f"\n{'[DRY RUN] ' if args.dry_run else ''}Summary:")
    print(f"  Files modified: {total_modified}")
    print(f"  Functions renamed: {total_renamed}")
    
    if args.dry_run:
        print("\nRun without --dry-run to apply changes")
    elif total_modified > 0:
        print("\n✅ Renaming complete!")
        print("Note: Added comments with original test names for readability.")
        print("Next steps:")
        print("  1. Verify the changes look correct")
        print("  2. Run: ./gradlew :app:dexBuilderDebugAndroidTest")
        print("  3. If successful, run: ./gradlew clean")

if __name__ == '__main__':
    main()

