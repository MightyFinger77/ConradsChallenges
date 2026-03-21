# Changelog

## 4.3.0-Dev1a (2026-03-19)

### Added
- Added Folia metadata and compatibility baseline in this version line.
- Created a dedicated versioned copy to preserve previous latest release behavior.
- Added lightweight Folia scheduler shim for core repeating runtime tasks.
- Added Modrinth update checker (`update-checker.enabled`) with async API check + console notice.

### Changed
- **Semver**: minor bump for this dev line (`4.2.6` → `4.3.0`); patch reset to `0`.
- Standardized release metadata and version identifiers for this Folia-targeted build.
- Aligned update notes/docs structure for consistent release management.

### Notes
- This release is intended as a safe Folia-forward branch and avoids destructive rewrites in legacy branches.
