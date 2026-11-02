# Security checks

`Security` runs on main pushes, pull requests, manual dispatch and Mondays at 07:41 UTC. Both jobs have read-only repository permissions, no repository secrets and immutable action/tool pins. Tooling is open source and needs no scanner account, API token or paid GitHub code-scanning feature; normal GitHub Actions minute/storage limits still apply.

## Secret scanning

Gitleaks **8.30.1** scans the complete checked-out Git history (`--all`) and redacts matches. `install_gitleaks.py` downloads the official release, checks a committed SHA-256 before extraction and installs only the expected executable. Linux x64 and local Apple Silicon archives are supported.

The default Gitleaks rules remain enabled. One exact, public signing-certificate fingerprint is allowlisted for the generic-key rule **only in README.md and docs/TESTING.md**. Those documents describe it as the app key, which caused four false positives. This does not exempt private keys, other hashes/values, entire files, commits or test directories. Future findings should be investigated; never automatically baseline them.

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

The pinned tool versions and local rules require deliberate updates. Weekly runs recheck the current repository but do not silently download newer rules. Dependency alerts, Gradle dependency submission and Android lint are managed by the repository's separate workflows.

## Initial validation

On 2 November 2026: Gitleaks scanned seven commits and found no secrets after the exact public-fingerprint exception; Semgrep 1.183.0 passed all seven rule tests and scanned 49 production Kotlin files with no findings and approximately 100% parsed lines; the five surface mutation tests passed. Local Semgrep used the exact CLI version in an isolated environment. The pinned image's upstream manifest/config and command entry point were verified; Docker execution is verified by GitHub CI, not the local Mac session.

Primary references: [Gitleaks v8.30.1](https://github.com/gitleaks/gitleaks/tree/v8.30.1), [Semgrep v1.183.0](https://github.com/semgrep/semgrep/releases/tag/v1.183.0), [local rules](https://docs.semgrep.dev/running-rules), [rule testing](https://docs.semgrep.dev/writing-rules/testing-rules).
