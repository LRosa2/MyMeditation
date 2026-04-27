#!/usr/bin/env python3
"""Convert exported sittings from external app to MyMeditation CSV import format.

Input format per line: prefix<sep>start_time<sep>end_time<sep>duration_ms<sep>
Example: \u0F04\u0FCC2015-02-02 10:53:04\u0FCC2015-02-02 11:23:15\u0FCC1800000\u0FCC

Output CSV format: id,session_name,start_time,duration_seconds,duration_formatted
"""

import sys
from datetime import datetime

SEP = '\u0FCC'  # Tibetan symbol separator
PREFIX = '\u0F04'  # Tibetan prefix

def format_duration(total_seconds):
    h = total_seconds // 3600
    m = (total_seconds % 3600) // 60
    s = total_seconds % 60
    return f"{h}h{m:02d}m{s:02d}s"

def convert(input_path, output_path):
    with open(input_path, 'r', encoding='utf-8-sig') as f:
        lines = f.readlines()

    with open(output_path, 'w', encoding='utf-8') as out:
        out.write("id,session_name,start_time,duration_seconds,duration_formatted\n")
        line_num = 0
        for raw_line in lines:
            line = raw_line.strip()
            if not line:
                continue
            # Split by separator and filter empty strings
            parts = [p for p in line.split(SEP) if p]
            # Format is: [prefix?, start, end, duration]
            if len(parts) < 3:
                print(f"Skipping malformed line: {line[:80]}")
                continue
            
            # Use negative indexing: last = duration, second-to-last = end time, third-to-last = start time
            duration_ms = parts[-1]
            end_time = parts[-2]
            start_time = parts[-3]
            
            try:
                duration_sec = int(duration_ms) // 1000
                # Validate date format
                datetime.strptime(start_time, "%Y-%m-%d %H:%M:%S")
            except (ValueError, IndexError) as e:
                print(f"Skipping invalid line: {line[:80]} ({e})")
                continue
            
            duration_fmt = format_duration(duration_sec)
            line_num += 1
            out.write(f'{line_num},"Imported",{start_time},{duration_sec},"{duration_fmt}"\n')
        
        print(f"Converted {line_num} entries to {output_path}")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        input_file = r"D:\temp\exported-sittings-20260427161016.txt"
        output_file = r"D:\temp\sittings_import.csv"
    else:
        input_file = sys.argv[1]
        output_file = sys.argv[2] if len(sys.argv) > 2 else input_file.replace('.txt', '.csv')
    
    convert(input_file, output_file)
