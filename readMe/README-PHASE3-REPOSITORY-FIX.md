# SakhtYar Phase 3 Repository Fix

Base reviewed commit: `f9c4c20c7a1b377a3def7bfc0931800581ada025` (`main`).

## What was wrong

The Phase 3 commit contained `AssistantPanel.tsx` and persistence/glossary classes, but the repository was only partially wired:

1. `CaseDetailPage.tsx` did not import or render `AssistantPanel`, so the Assistant tab was invisible.
2. `frontend/src/api/types.ts` did not contain the Agent/Conversation/Glossary contracts used by `AssistantPanel`.
3. `AssistantPanel` calls `GET /api/v1/agents/conversations/{id}/messages`, but `AgentController` did not expose that endpoint.
4. `AgentConversationService` existed but was not used by `AgentController`, so messages and runs were not persisted through `/chat`.
5. Frontend expected `schemaVersion`, `caseId`, `inputs`, and `assumptions` in `AgentWorkflowResult`, but the backend record did not expose them.
6. `AgentRegistry` did not expose descriptor metadata even though `AgentDescriptor` existed.
7. The Agents module directly uses JPA and Spring Security core types; direct dependencies are now declared instead of relying on transitive dependencies.

## Files changed

- `backend/modules/agents/pom.xml`
- `backend/modules/agents/src/main/java/com/sakhtyar/agents/api/AgentController.java`
- `backend/modules/agents/src/main/java/com/sakhtyar/agents/core/AgentDescriptor.java`
- `backend/modules/agents/src/main/java/com/sakhtyar/agents/core/AgentRegistry.java`
- `backend/modules/agents/src/main/java/com/sakhtyar/agents/orchestrator/AgentOrchestrator.java`
- `backend/modules/agents/src/main/java/com/sakhtyar/agents/orchestrator/AgentWorkflowResult.java`
- `frontend/src/api/types.ts`
- `frontend/src/features/case/AssistantPanel.tsx`
- `frontend/src/pages/CaseDetailPage.tsx`

The existing Phase 3 migration `V5__phase3_agents.sql`, glossary persistence, conversation entities, and Persian Agent code from the repository are retained.

## Apply on Windows

From the extracted patch directory:

```powershell
.\apply-phase3-repository-fix.ps1 -Repo "C:\path\to\SakhtYar"
```

Then:

```powershell
cd C:\path\to\SakhtYar\backend
mvn clean test

cd ..\frontend
npm run build
npm run dev
```

Restart `SakhtYarApplication` after the backend build.

## Expected UI

Open a case. Tabs should include:

- خلاصه
- مشخصات ملک
- مالکین
- نقشه
- مدارک
- دستیار هوشمند

## Smoke tests

### Agent list

`GET /api/v1/agents`

### Agent registry

`GET /api/v1/agents/registry`

### AI status

`GET /api/v1/agents/ai/status`

### Chat

`POST /api/v1/agents/chat`

Example body:

```json
{
  "conversationId": null,
  "caseId": "<CASE_UUID>",
  "message": "این زمین 250 متره، برش 12 متره و منطقه 5 تهرانه",
  "parameters": {}
}
```

The response should include `schemaVersion: "1.0"`, `inputs`, `assumptions`, `results`, and `missingFields`.

### Conversation history

`GET /api/v1/agents/conversations/{conversationId}/messages`

After a chat, `agent_conversation`, `agent_message`, and `agent_run` should receive records.

## Notes

- `frontageM` is currently an extracted Agent field but is not yet part of the Phase 1 Property persistence model, so Apply-to-Case does not persist it.
- Municipality Agent must not invent official buildability rules when they are missing; `NEEDS_INPUT` or `PARTIAL` is valid behavior.
