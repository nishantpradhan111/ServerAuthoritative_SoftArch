# Rubric Evidence Map

This file maps the evaluation criteria to the concrete implementation and documentation artifacts in the repository.

| Criterion | Evidence |
|---|---|
| Correct application of chosen architectural pattern | `docs/evaluation/architecture_views_mermaid.md`, `docs/evaluation/architecture_decision_log.md`, `src/main/java/com/codereboot/gameboot/application/RoomService.java`, `src/main/java/com/codereboot/gameboot/security/SecurityConfig.java` |
| Fulfilment of functional requirements | `src/main/java/com/codereboot/gameboot/api/AuthController.java`, `src/main/java/com/codereboot/gameboot/api/RoomController.java`, `src/main/java/com/codereboot/gameboot/transport/GameSocketCommandDispatcher.java`, `src/main/resources/static/js/game.js` |
| Architectural design quality and adherence to principles | `src/main/java/com/codereboot/gameboot/domain/Room.java`, `src/main/java/com/codereboot/gameboot/domain/RoomCombatLifecycle.java`, `src/main/java/com/codereboot/gameboot/domain/ConeHitValidationPolicy.java`, `docs/evaluation/architecture_decision_log.md` |
| Quality and completeness of architectural documentation | `README.md`, `IMPLEMENTATION_SUMMARY.md`, `SETUP.md`, `DEPLOYMENT.md`, `docs/evaluation/architecture_views_mermaid.md`, `docs/evaluation/project_evaluation_report.md` |
| Code quality, modularity, and maintainability | `src/test/java/com/codereboot/gameboot/domain/RoomTest.java`, `src/test/java/com/codereboot/gameboot/transport/GameSocketCommandParserTest.java`, `src/test/java/com/codereboot/gameboot/transport/GameSocketCommandDispatcherTest.java` |
| Lightweight operational visibility and traceability | `src/main/java/com/codereboot/gameboot/api/SystemController.java`, `src/main/java/com/codereboot/gameboot/security/RequestIdFilter.java`, `src/main/java/com/codereboot/gameboot/api/ApiExceptionHandler.java`, `src/test/java/com/codereboot/gameboot/api/SystemControllerTest.java`, `src/test/java/com/codereboot/gameboot/security/RequestIdFilterTest.java` |

## Submission Notes

- The project now has a coherent story from architecture design to implementation to verification.
- The report is scored with scope in mind: the project intentionally favors a simple, principle-driven architecture over heavier operational tooling.
- The absence of Redis, Prometheus, or container orchestration is a design choice for this coursework scope, not an omission of the intended architectural style.
- The new health endpoint and request ID tracing show that the project can still provide useful runtime feedback without drifting into a heavyweight platform stack.
