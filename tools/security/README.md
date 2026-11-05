# Security checks

`Security` runs on main pushes, pull requests, manual dispatch and Mondays at 07:41 UTC. Both jobs have read-only repository permissions, no repository secrets and immutable action/tool pins. Tooling is open source and needs no scanner account, API token or paid GitHub code-scanning feature; normal GitHub Actions minute/storage limits still apply.

## Secret scanning

Gitleaks **8.30.1** scans the complete checked-out Git history (`--all`) and redacts matches. `install_gitleaks.py` downloads the official release, checks a committed SHA-256 before extraction and installs only the expected executable. Linux x64 and local Apple Silicon archives are supported.

The default Gitleaks rules remain enabled. One exact, public signing-certificate fingerprint is allowlisted for the generic-key rule **only in the historical README.md path, docs/TESTING.md and docs/RELEASE-HISTORY.md**. Those documents describe it as the app key, which caused four false positives. This does not exempt private keys, other hashes/values, entire files, commits or test directories. Future findings should be investigated; never automatically baseline them.

```sh
python3 tools/security/install_gitleaks.py /tmp/runline-gitleaks
/tmp/runline-gitleaks git . --log-opts="--all" --redact=100 --no-banner --config tools/security/gitleaks.toml
```

Git history scanning covers committed changes. For a local release, commit intended changes before the final scan; ignored signing files are deliberately outside Git.

## Source and Android boundaries

Semgrep Community Edition **1.183.0** runs seven committed Kotlin rules against `app/src/main/java`. Rules reject insecure cipher modes, world-readable storage, permissive hostname/certificate validation, ignored WebView SSL errors, universal file-origin access and direct logging of credential-named variables. Positive and negative fixtures protect every rule. These targeted patterns are not complete data-flow analysis or proof that the app has no vulnerabilities.

CI uses the immutable `semgrep/semgrep:1.183.0` image index `sha256:2e45517a85e13a9cca14a2a00df6bb48affd66b8ebfae1b0991cd57578362fb0`, which pins the CLI and its runtime dependencies. The image is pulled first; scans run without network access, with a read-only source mount, telemetry/version checks disabled and no registry-hosted rules. `--strict --error` makes findings and parsing/configuration problems fail CI.

The Python surface guard checks that backups/device transfers remain excluded, production debugging and application-wide cleartext traffic stay off, only the launcher is exported, and FileProvider exposes only prepared exports. Five mutation tests verify its failure paths.

```sh
python3 tools/security/test_android_surface.py
python3 tools/security/check_android_surface.py
# With Semgrep CE 1.183.0 installed:
SEMGREP_SEND_METRICS=off SEMGREP_ENABLE_VERSION_CHECK=0 semgrep test tools/security/rules
semgrep scan --config tools/security/rules --metrics=off --disable-version-check --strict --error --jobs 2 app/src/main/java
```

The pinned tool versions and local rules require deliberate updates. Weekly runs recheck the current repository but do not silently download newer rules. Dependency alerts, the advisory audit, Gradle dependency submission and Android lint are managed by the repository's separate workflows.

## Dependency advisories and toolchain limits

`Dependency advisory audit` runs on PRs, main pushes, manual dispatch and Mondays at 07:23 UTC. It has read-only permissions and no signing secrets. `export_dependencies.init.gradle` exports the selected Maven versions from every resolvable project and buildscript configuration, including the build plugins, lint tools, debug/release and test dependencies. Unresolved dependencies or an empty inventory fail the export. The checker queries the public [OSV batch API](https://google.github.io/osv.dev/post-v1-querybatch/) using the current project's public Maven `group:name` and version; source, repository identity, keys and recordings are not uploaded.

Every returned advisory fails the check, regardless of severity. Malformed inventories, missing responses, failed requests or unfinished pagination also fail; they are not treated as a clean result. Seventeen offline tests cover matching, distinct versions, batching, pagination, malformed responses and request failures. CI retains the inventory and advisory JSON reports for 14 days, including failed audits. Only after a successful audit may the separate trusted-main job submit the dependency graph to GitHub with `contents: write`; PRs never run that job.

```sh
python3 tools/security/test_dependency_advisories.py
./gradlew --no-daemon --no-configuration-cache --init-script tools/security/export_dependencies.init.gradle exportDependencyAudit
python3 tools/security/check_dependency_advisories.py --input build/reports/dependency-audit.json --output build/reports/dependency-advisories.json
```

The patched project toolchain is AGP **9.5.0**, AGP built-in Kotlin with a **2.4.21** classpath override, Gradle **9.8.2** and JDK **17**, with compile SDK **37** and target SDK **34** and Build Tools **36.0.0**. Narrow root-buildscript constraints select Guava **33.7.2-jre**, jose4j **0.9.6**, JDOM **2.0.6.1** and Commons Lang **3.18.0**; the Bouncy Castle **1.86** BOM aligns its modules. The separate `androidLintTool` configuration receives constraints for Guava, Commons Lang, HttpClient **4.5.14** and the three Bouncy Castle modules, preserving AGP's default lint dependency. These do not globally force application dependencies or replace Gradle's own libraries. Gradle Actions uses the [free basic cache provider](https://github.com/gradle/actions/blob/v6.4.1/README.md), avoiding the Enhanced Caching preview for private repositories.

**Known limitation:** the official [Gradle 9.8.2 distribution catalog](https://github.com/gradle/gradle/blob/v9.8.2/gradle/dependency-management/distribution.versions.toml) still selects Guava **33.4.6-jre**, within the affected range for [GHSA-xxph-c9ww-hj94](https://github.com/advisories/GHSA-xxph-c9ww-hj94). Project constraints cannot patch that bundled JAR; there is no supported project-level override for the distribution library. Runline retains the official checksum-verified distribution and must track an upstream distribution fix. The advisory concerns native Java deserialization; its presence does not establish an exploitable path in Runline, but it remains an unresolved toolchain finding.

This Maven check does not audit the Gradle distribution, JDK, Android SDK, scanner containers, or plugins injected separately by GitHub Actions. It covers the exported project graph against OSV's current data, so a green result is not a vulnerability-free claim. The public repository enforces required checks through branch protection; the reviewer must also verify every applicable check for the PR's current head before merging, as described in [repository checks](../../docs/CHECKS.md).

## Initial validation

On 2 November 2026: Gitleaks scanned seven commits and found no secrets after the exact public-fingerprint exception; Semgrep 1.183.0 passed all seven rule tests and scanned 49 production Kotlin files with no findings and approximately 100% parsed lines; the five surface mutation tests passed. Local Semgrep used the exact CLI version in an isolated environment. The pinned image's upstream manifest/config and command entry point were verified; Docker execution is verified by GitHub CI, not the local Mac session.

Primary references: [Gitleaks v8.30.1](https://github.com/gitleaks/gitleaks/tree/v8.30.1), [Semgrep v1.183.0](https://github.com/semgrep/semgrep/releases/tag/v1.183.0), [local rules](https://docs.semgrep.dev/running-rules), [rule testing](https://docs.semgrep.dev/writing-rules/testing-rules).
