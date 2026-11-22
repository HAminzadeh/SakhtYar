---
disable-model-invocation: true
name: sakhtyar-skill-development
description: Develop and evaluate project-specific Claude skills from approved SakhtYar engineering guidance.
origin: sakhtyar-custom
maturity: adapted
---
# SakhtYar Skill Development

1. Identify the concrete repeated workflow and measurable benefit.
2. Read only its existing source entry under `engineering/ai-harness/skills/` and related project standards.
3. Convert catalog drafts into concise, executable human-verifiable instructions; avoid redundant all-project guidance.
4. Include input constraints, minimal steps, output format and safety rules; avoid executable remote scripts.
5. Test with a representative success case and a negative case. Mark readiness `adapted` only after human review.
6. Keep skill description short. Put long support material in referenced files so it is loaded only when used.
7. Record version/source digest and keep rollback possible.

Do not install, activate or download third-party skills silently.
