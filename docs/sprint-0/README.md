# Sprint 0

Sprint 0 is about proving the model and runtime path before we commit the rest of the app to it.

Artifacts in this folder:

- `adrs/`: architecture decisions accepted for the first implementation phase
- `benchmark-results.md`: observed on-device runtime and validation findings
- `model-contract.md`: expected model output and validation file format
- `performance-budget.md`: latency, accuracy, and memory targets
- `user-inputs-required.md`: the exact items still needed from you

Code artifacts added with this sprint:

- lightweight ML contract types in `app/src/main/java/in/c1ph3rj/scanly/core/ml/`
- a readiness screen in `app/src/main/java/in/c1ph3rj/scanly/feature/readiness/`
- placeholder asset locations under `app/src/main/assets/`
- geometry validation tests for ordered document corners
