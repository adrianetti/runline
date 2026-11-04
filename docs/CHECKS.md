# Repository checks

Runline uses GitHub Actions on pushes to `main` and pull requests. Workflow files and tool versions are committed, actions are pinned to full commit IDs, and normal checks have read-only repository permissions. Pull requests never receive the app signing key. There is no automatic merge of dependency changes.

- **Android tests, lint and APK:** JVM tests, Android lint and debug/release compilation. Lint errors fail the job; existing warnings remain visible in uploaded reports. Main push builds also produce the signed APK. Review builds use no private signing credentials.
- **Resources and wrapper:** validate the Gradle wrapper JAR, all English/Spanish resource keys/types/format arguments, pinned Tabler assets and whitespace. The Gradle distribution has a pinned SHA-256 checksum.
- **Workflow lint:** actionlint validates workflow syntax, expressions and embedded shell. Its downloaded binary is versioned and checksum-verified.
- **Dependency advisory audit:** resolves actual Maven versions across every resolvable project and buildscript configuration, including lint, debug, release and tests, then queries OSV. Any known finding or incomplete check fails the job; there is no severity filter or ignored-advisory list. It runs on PRs, main pushes, manual dispatch and weekly, with read-only permissions. Seventeen offline tests cover the checker. Only public dependency coordinates and versions are sent to OSV; reports are uploaded even when the audit fails.
- **Dependency inventory:** after a successful audit, a separate trusted-main job submits the Gradle graph to this private repository so Dependabot can identify vulnerable direct and transitive dependencies. It runs on main pushes, manual dispatch on main and weekly. Only this job has `contents: write`, and it has no signing key. It never runs for PRs.
- **Dependabot:** weekly Gradle and GitHub Actions update PRs, with related AndroidX/Material version updates grouped together and security updates kept separate, plus enabled vulnerability alerts and security-update PRs. Updates still require review and passing checks; this does not mean every dependency is currently vulnerability-free.

**Secret scanning** uses checksum-pinned Gitleaks over Git history. **Android source security** runs seven tested local Semgrep rules and five tested guards for backup, exported components and file-sharing boundaries. Details and limitations are in [security checks](../tools/security/README.md). Scanners run on GitHub-hosted runners and do not send source to a separate scanning account. These checks catch known patterns and advisories; they do not replace device tests or a security review.

The build uses AGP **9.5.0**, AGP's built-in Kotlin with the documented **2.4.21** classpath override, Gradle **9.8.2**, JDK **17**, Android compile SDK **37** / target SDK **34** and Build Tools **36.0.0**. Security constraints apply only to the root buildscript and isolated lint tool classpaths. Gradle Actions explicitly uses `cache-provider: basic`, which stays free for private repositories; its default Enhanced Caching is a [private-repository free preview](https://github.com/gradle/actions/blob/v6.4.1/README.md).

A clean Maven audit does not mean the whole toolchain is vulnerability-free. Gradle 9.8.2 still bundles affected Guava 33.4.6-jre; the [known distribution limitation](../tools/security/README.md#dependency-advisories-and-toolchain-limits) is separate from the project's patchable Maven graph.

Local checks:

```sh
python3 tools/check_resources.py
python3 tools/import_icons.py --check
./gradlew testDebugUnitTest assembleDebug assembleRelease lintDebug
python3 tools/security/test_dependency_advisories.py
./gradlew --no-daemon --no-configuration-cache --init-script tools/security/export_dependencies.init.gradle exportDependencyAudit
python3 tools/security/check_dependency_advisories.py --input build/reports/dependency-audit.json --output build/reports/dependency-advisories.json
# After installing the pinned actionlint release:
actionlint
```

The repository’s current free GitHub plan rejects private-repository branch protection (HTTP 403: Pro or public repository required). Checks report pass/fail on commits and PRs, but required-check merge enforcement is not enabled. The repository remains private. Before merging, the human or agent reviewer must verify the PR's current head SHA and require success from every applicable check for that exact revision. Pending, failed, cancelled or incomplete checks do not qualify. A new commit invalidates earlier approval and requires another check; the trusted-main inventory is intentionally skipped on PRs.

A release is published after its source checks pass and its exact signed APK has been verified. Main signing secrets remain repository secrets; local signing files remain ignored.

References: [Dependabot configuration](https://docs.github.com/en/code-security/reference/supply-chain-security/dependabot-options-reference), [Gradle dependency submission](https://github.com/gradle/actions/blob/main/dependency-submission/README.md), [wrapper validation](https://github.com/gradle/actions/blob/main/wrapper-validation/README.md), [actionlint](https://github.com/rhysd/actionlint).
