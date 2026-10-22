#!/usr/bin/env python3
"""仅读取指定实验PID的/proc计数和实验H2文件大小，不连接数据库或增加API。"""
import json
import os
from pathlib import Path
import sys
import time

if len(sys.argv) != 4 or not sys.argv[1].isdigit():
    raise SystemExit('用法：perf-sample.py PID 实验数据目录 全新JSONL路径')
pid = int(sys.argv[1])
data = Path(sys.argv[2])
output = Path(sys.argv[3])
ticks = os.sysconf('SC_CLK_TCK')
page_size = os.sysconf('SC_PAGE_SIZE')
previous = None
identity = None
deadline = time.monotonic() + 3600
with output.open('x') as stream:
    os.chmod(output, 0o600)
    while time.monotonic() < deadline:
        try:
            # comm字段允许空格；从最后右括号后取固定/proc stat字段。
            fields = Path(f'/proc/{pid}/stat').read_text().rsplit(')', 1)[1].split()
            started = fields[19]
            if identity is not None and started != identity:
                break
            identity = started
            cpu_ticks = int(fields[11]) + int(fields[12])
            rss = int(Path(f'/proc/{pid}/statm').read_text().split()[1]) * page_size
        except (FileNotFoundError, ProcessLookupError):
            break
        now = time.monotonic()
        database = data / 'knowledge.mv.db'
        record = {
            'epochSeconds': time.time(), 'pid': pid, 'cpuSeconds': cpu_ticks / ticks,
            'cpuPercentOneCore': None if previous is None else (cpu_ticks - previous[0]) / ticks / (now - previous[1]) * 100,
            'rssBytes': rss, 'h2FileBytes': database.stat().st_size if database.exists() else 0,
        }
        # 文件体积不是数据库积压或执行器队列长度，不能据此推导精确队列峰值。
        stream.write(json.dumps(record) + '\n')
        stream.flush()
        previous = (cpu_ticks, now)
        time.sleep(1)
