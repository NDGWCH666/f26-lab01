#!/usr/bin/env python3
"""export-transcripts-workbuddy.py: export WorkBuddy agent sessions into transcripts/.

The course ships `tools/export-transcripts.sh` for Claude Code. This is the
equivalent for WorkBuddy (see the handout, section 6.1: with any other tool the
export is your responsibility, and "equivalent" means complete -- your prompts,
the agent's replies, and the actions it took, in an order a reader can follow).

What it does:
  1. Finds WorkBuddy sessions under ~/.workbuddy/projects/<encoded-dir>/*.jsonl
     (override the config dir with WORKBUDDY_CONFIG_DIR).
  2. Keeps the sessions that ran against this repository -- either the encoded
     folder name is this repo, or the session log mentions the repo path.
  3. Copies each raw session log into transcripts/ untouched, and also writes a
     human-readable <session>.md rendering (prompt / reply / tool call / result).
  4. prints a summary.

The raw .jsonl is the authoritative record and keeps everything, including the
agent's internal reasoning. The .md drops that reasoning and truncates very long
tool output, purely so a reader can follow the session.

transcripts/ is in .gitignore. For Lab 1 it must NOT be committed or pushed (the
fork is public); bring the files to recitation and show them on your machine.

Usage (from anywhere inside your course repository):
    python tools/export-transcripts-workbuddy.py
"""

from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime
from pathlib import Path

MAX_TEXT_CHARS = 20_000
MAX_ARGS_CHARS = 2_000
MAX_RESULT_CHARS = 3_000
SYSTEM_REMINDER = re.compile(r"<system-reminder\b[^>]*>.*?</system-reminder>", re.DOTALL)


def repo_root() -> Path:
    try:
        out = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            capture_output=True, text=True, check=True,
        ).stdout.strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        sys.exit("error: this is not a git repository. Run the script from inside your course repository.")
    return Path(out).resolve()


def encode(path: str) -> str:
    """WorkBuddy names a project folder after the session's start directory,
    with every character that is not a letter or digit replaced by '-'."""
    return re.sub(r"[^A-Za-z0-9]", "-", path)


def needle_forms(root: Path) -> set[str]:
    """The ways this repository's path can appear in a session log.

    WorkBuddy sessions are keyed by the directory the session started in, which for
    this course is the WorkBuddy workspace, not the repository. So a session is
    matched by whether its log mentions the repository. Session logs are JSON, so a
    Windows path shows up with doubled backslashes; shell commands may use the
    MSYS form (/c/Users/...) with a lower-case drive letter.
    """
    text = str(root)
    return {
        text,                                                    # C:\a\b
        text.replace("\\", "\\\\"),                              # C:\\a\\b  (JSON-escaped)
        text.replace("\\", "/"),                                 # C:/a/b
        "/" + text[0].lower() + text[2:].replace("\\", "/"),     # /c/a/b   (Git Bash / MSYS)
        root.name,                                               # the directory name alone
    }


def truncate(text: str, limit: int) -> str:
    if len(text) <= limit:
        return text
    dropped = len(text) - limit
    return f"{text[:limit]}\n\n[... {dropped:,} more characters omitted; see the raw .jsonl ...]"


def stamp(ms) -> str:
    if not isinstance(ms, (int, float)):
        return "?"
    return datetime.fromtimestamp(ms / 1000).strftime("%Y-%m-%d %H:%M:%S")


def fence(body: str) -> str:
    """Four backticks, so nested three-backtick code blocks survive."""
    return "````\n" + body.rstrip() + "\n````\n"


def record_time(record: dict) -> float:
    ts = record.get("timestamp")
    return float(ts) if isinstance(ts, (int, float)) else 0.0


def models_used(records: list[dict]) -> list[str]:
    seen: list[str] = []
    for record in records:
        provider = record.get("providerData")
        model = provider.get("model") if isinstance(provider, dict) else None
        if model and model not in seen:
            seen.append(model)
    return seen


def render_user(record: dict) -> str:
    parts = ["### 用户\n"]
    for block in record.get("content") or []:
        if not isinstance(block, dict):
            continue
        kind = block.get("type")
        if kind == "input_text":
            text = SYSTEM_REMINDER.sub("", block.get("text") or "").strip()
            if text:
                parts.append(truncate(text, MAX_TEXT_CHARS) + "\n")
        else:
            # Image attachments and anything else: name it, keep the raw detail short.
            detail = json.dumps({k: v for k, v in block.items() if k != "providerData"},
                                ensure_ascii=False)
            parts.append(f"[附件: {kind}] `{truncate(detail, 300)}`\n")
    return "\n".join(parts)


def render_assistant(record: dict) -> str:
    parts = ["### 助手\n"]
    for block in record.get("content") or []:
        if isinstance(block, dict) and block.get("type") == "output_text":
            text = (block.get("text") or "").strip()
            if text:
                parts.append(truncate(text, MAX_TEXT_CHARS) + "\n")
    model = (record.get("providerData") or {}).get("model")
    if model:
        parts.append(f"_(由 {model} 生成)_\n")
    return "\n".join(parts)


def render_call(record: dict) -> str:
    try:
        args = json.dumps(json.loads(record.get("arguments") or "{}"), ensure_ascii=False, indent=2)
    except (json.JSONDecodeError, TypeError):
        args = str(record.get("arguments"))
    return f"### 工具调用 · `{record.get('name')}`\n" + fence(truncate(args, MAX_ARGS_CHARS))


def render_result(record: dict) -> str:
    output = record.get("output")
    text = output.get("text") if isinstance(output, dict) else str(output)
    header = f"### 工具结果 · `{record.get('name')}` ({record.get('status', '?')})\n"
    return header + fence(truncate(text or "", MAX_RESULT_CHARS))


def render_session(session_id: str, records: list[dict], source: Path, root: Path) -> str:
    title = next((r.get("aiTitle") for r in records if r.get("type") == "ai-title"), None)
    models = models_used(records)
    first = next((r for r in records if r.get("timestamp")), {})
    last = records[-1] if records else {}

    head = [
        f"# 智能体会话记录 · {title or session_id}",
        "",
        "| | |",
        "|---|---|",
        "| 工具 | WorkBuddy（桌面客户端） |",
        f"| 模型 | {', '.join(models) if models else '未记录'}（Auto 路由） |",
        f"| 仓库 | `{root}` |",
        f"| 会话 ID | `{session_id}` |",
        f"| 时间 | {stamp(first.get('timestamp'))} — {stamp(last.get('timestamp'))} |",
        f"| 原始日志 | `{source.name}`（本目录中的同名 .jsonl，未删改） |",
        "",
        "本文件是原始日志的可读渲染：按时间顺序保留用户提示词、智能体回复、工具调用与"
        "工具结果。智能体的内部推理过程与超过 3000 字符的工具输出（多为源码与测试日志）"
        "被省略或截断，完整内容见同一目录下的 .jsonl 原始文件。系统提示注入的 "
        "`<system-reminder>` 块已从用户提示中移除。",
        "",
        "---",
        "",
    ]

    body: list[str] = []
    for record in sorted(records, key=record_time):
        kind = record.get("type")
        if kind == "message" and record.get("role") == "user":
            body.append(render_user(record))
        elif kind == "message" and record.get("role") == "assistant":
            body.append(render_assistant(record))
        elif kind == "function_call":
            body.append(render_call(record))
        elif kind == "function_call_result":
            body.append(render_result(record))
    return "\n".join(head) + "\n".join(body) + "\n"


def main() -> int:
    root = repo_root()
    config_dir = Path(os.environ.get("WORKBUDDY_CONFIG_DIR", Path.home() / ".workbuddy"))
    projects_dir = config_dir / "projects"
    if not projects_dir.is_dir():
        sys.exit(
            f"error: {projects_dir} not found.\n"
            "WorkBuddy stores its sessions there. Has WorkBuddy run on this machine?"
        )

    encoded = encode(str(root))
    needles = needle_forms(root)
    dest = root / "transcripts"
    dest.mkdir(exist_ok=True)

    exported = 0
    skipped = 0

    for project in sorted(p for p in projects_dir.iterdir() if p.is_dir()):
        exact = project.name == encoded
        for session in sorted(project.glob("*.jsonl")):
            text = session.read_text(encoding="utf-8", errors="replace")
            if not exact and not any(n in text for n in needles):
                skipped += 1
                continue

            session_id = session.stem
            records = []
            for line in text.splitlines():
                line = line.strip()
                if not line:
                    continue
                try:
                    records.append(json.loads(line))
                except json.JSONDecodeError:
                    continue

            shutil.copy2(session, dest / session.name)
            (dest / f"{session_id}-readable.md").write_text(
                render_session(session_id, records, session, root), encoding="utf-8"
            )
            print(f"exported {session.name}  ({len(records)} records, {len(text) // 1024}K)")
            exported += 1

    if exported == 0:
        print(f"No WorkBuddy sessions found for {root}.", file=sys.stderr)
        print("Sessions are recorded per start directory: open this repository in "
              "WorkBuddy and work there, then run this script again.", file=sys.stderr)
        if skipped:
            print(f"(Skipped {skipped} session file(s) that never mentioned this repository.)", file=sys.stderr)
        return 1

    print(f"\nExported {exported} session(s) to {dest}")
    if skipped:
        print(f"Skipped {skipped} session file(s) that never mentioned this repository.")
    print()
    print("Lab 1: do NOT commit or push these files. transcripts/ is in .gitignore, and the")
    print("handout asks you to show them on your own machine at recitation.")
    print("  git status             # transcripts/ must not appear")
    print("  git check-ignore -v transcripts/")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
