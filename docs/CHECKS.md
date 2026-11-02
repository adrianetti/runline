# Repository checks

Runline uses GitHub Actions on pushes to `main` and pull requests. Workflow files and tool versions are committed, actions are pinned to full commit IDs, and normal checks have read-only repository permissions. Pull requests never receive the app signing key. There is no automatic merge of dependency changes.

- **Android tests, lint and APK:** JVM tests, Android lint and debug/release compilation. Lint errors fail the job; existing warnings remain visible in uploaded reports. Main push builds also produce the signed APK. Review builds use no private signing credentials.
- **Resources and wrapper:** validate the Gradle wrapper JAR, all English/Spanish resource keys/types/format arguments, pinned Tabler assets and whitespace. The Gradle distribution has a pinned SHA-256 checksum.
- **Workflow lint:** actionlint validates workflow syntax, expressions and embedded shell. Its downloaded binary is versioned and checksum-verified.
- **Dependency inventory:** a separate trusted-main job resolves the Gradle graph and submits it to this private repository so Dependabot can identify vulnerable direct and transitive dependencies. It runs on main pushes and weekly. Only this job has `contents: write`, and it has no signing key.
- **Dependabot:** weekly Gradle and GitHub Actions update PRs, plus enabled vulnerability alerts and security-update PRs. Updates still require review and passing checks; this does not mean every dependency is currently vulnerability-free.

**Secret scanning** uses checksum-pinned Gitleaks over Git history. **Android source security** runs seven tested local Semgrep rules and five tested guards for backup, exported components and file-sharing boundaries. Details and limitations are in [security checks](../tools/security/README.md). Scanners run on GitHub-hosted runners and do not send source to a separate scanning account. These checks catch known patterns and advisories; they do not replace device tests or a security review.

Local checks:

```sh
python3 tools/check_resources.py
python3 tools/import_icons.py --check
./gradlew testDebugUnitTest assembleDebug assembleRelease lintDebug
# After installing the pinned actionlint release:
actionlint
```

The repository’s current GitHub plan rejects private-repository branch protection (HTTP 403: Pro or public repository required). Checks report pass/fail on commits and PRs, but required-check merge enforcement is not enabled. The repository remains private.

A release is published after its source checks pass and its exact signed APK has been verified. Main signing secrets remain repository secrets; local signing files remain ignored.

References: [Dependabot configuration](https://docs.github.com/en/code-security/reference/supply-chain-security/dependabot-options-reference), [Gradle dependency submission](https://github.com/gradle/actions/blob/main/dependency-submission/README.md), [wrapper validation](https://github.com/gradle/actions/blob/main/wrapper-validation/README.md), [actionlint](https://github.com/rhysd/actionlint).
