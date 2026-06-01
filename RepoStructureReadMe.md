# Repository Structure

This repo is organized around application folders plus a shared UI library.

## Top-Level Folders

- `.Library/`
  - Shared frontend assets that can be used by any app.
  - UI components live in `.Library/Components/`.
  - Shared styles live in `.Library/Styles/`.
  - This folder is not a Maven module.

- `<ApplicationName>/`
  - Each application owns its backend and frontend code.
  - Backend code lives in `<ApplicationName>BE/`.
  - UI code lives in `<ApplicationName>UI/`.
  - Each application folder has its own `pom.xml`.

## Maven

- The root `pom.xml` is the workspace aggregator.
- The root `pom.xml` includes application modules only.
- Each application `pom.xml` owns the build configuration for that application's backend and UI folders.
- `.Library/` is consumed by UI projects directly and is not listed as a Maven module.

## UI Rules

- App UI folders contain app bootstrap code, app-specific configuration, and serving/build setup.
- Reusable UI components belong in `.Library/Components/`.
- Reusable CSS belongs in `.Library/Styles/`.
- App UI projects should import from `.Library/` rather than duplicating shared components or styles.
