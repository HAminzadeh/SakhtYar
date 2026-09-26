# SakhtYar V2 Foundation 03

Target: branch v2
Base commit: 6c1e14c520445626d7c8c24f3e848a39dd38084c

Scope:
- Identity response mapping moved to MapStruct.
- Canonical XML contract validator added to shared-kernel.
- Urban query XSD upgraded from placeholder to typed canonical contract.
- Tests for XML validation.
- No JWT/login/security behavior change.
- No artificial Repository/Entity layer added to Geo (Geo is provider/integration oriented).

Apply:
  .\apply-v2-foundation-03.ps1 -Repo "D:\ChatGPT_Projects\SakhtYar\source"

Then:
  cd D:\ChatGPT_Projects\SakhtYar\source\backend
  mvn clean install
