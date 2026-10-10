# Lab 1 Starter: Booking Service

A small room-booking service. Users book rooms for time intervals, and if a room is
taken they land on a waitlist. It is the codebase you work in for Lab 1.

**Read `ARCHITECTURE.md` first.** It maps the three layers (domain / service / repo)
so you do not have to cold-read every file.

## Build and test

```
mvn test
```

One test fails on purpose. Diagnosing and fixing it is Milestone 1.
By the way: the fix may not be where the failing test first points you. :-)

## Where things are

- Source: `src/main/java/edu/cmu/cs214/booking/` (`domain/`, `service/`, `repo/`)
- Tests: `src/test/java/edu/cmu/cs214/booking/`
- Setup: `SETUP.md`
- Your Milestone 2 task: `TASK.md`
- A proposed change you will review in Milestone 3: `changes/agent-attempt.patch` (the handout tells you how to apply it)
- Transcript export script (for Claude Code; modify if using a different tool): `tools/export-transcripts.sh` (the handout tells you when to run it)
- WorkBuddy transcript export: `tools/export-transcripts-workbuddy.py` (the adaptation used here; writes the raw `.jsonl` plus a readable `.md` into `transcripts/`)

See the Lab 1 handout on the course page for the three milestones you show a TA.

## 工具与模型声明

- 工具：**WorkBuddy**（桌面客户端），通过其内置终端执行构建与 git 命令
- 模型：**Auto** 路由；本实验会话中实际使用的模型为 `deepseek-v4.1-flash` 与 `glm-5.3`
- 环境：JDK 21（Microsoft OpenJDK 21.0.10）、Maven 3.9.16，依赖走阿里云镜像；测试用 `mvn test` 运行
- 会话导出：运行 `python tools/export-transcripts-workbuddy.py`，输出在 `transcripts/`（按实验要求**不提交、不推送**，仅在实验课上向助教出示）

