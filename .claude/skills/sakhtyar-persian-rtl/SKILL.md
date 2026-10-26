---
disable-model-invocation: true
name: sakhtyar-persian-rtl
description: Review Persian RTL UI, typography, accessibility and mixed-language layouts in SakhtYar React/Ant Design and JavaFX.
origin: sakhtyar-custom
maturity: adapted
---
# SakhtYar Persian RTL QA

Use this skill only for Persian/English interfaces in SakhtYar. Examine only components or layouts relevant to the task.

1. Verify direction is RTL for Persian and LTR for English at the component/layout container, not by reversing source strings.
2. Keep Latin code, paths, URLs, phone numbers, IPs, and technical values readable with suitable bidi isolation.
3. Check text overflow, mixed digits, focus order, labels, dialogs, keyboard navigation, ARIA and contrast.
4. Verify Ant Design components and JavaFX NodeOrientation behavior in both languages.
5. Report file-specific findings with severity and a minimal regression test plan; do not claim UI verification without running tests.

Avoid global CSS hacks that silently reverse code blocks or log output.
