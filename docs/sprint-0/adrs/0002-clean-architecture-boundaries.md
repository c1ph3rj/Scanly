# ADR 0002: Clean Architecture Boundaries

- Status: Accepted for Sprint 0

## Context

The repo is currently a single-module Compose app. Splitting into many Gradle modules immediately would slow early delivery, but leaving everything flat would make camera, ML, storage, and export code hard to maintain.

## Decision

Keep one Gradle module for now, but enforce clean architecture through package boundaries:

- `core`
- `domain`
- `data`
- `feature`
- `navigation`
- `di`

## Consequences

- Sprint 1 can move fast without a large Gradle refactor
- contracts can stabilize before any multi-module extraction
- code review can still enforce separation by package ownership
