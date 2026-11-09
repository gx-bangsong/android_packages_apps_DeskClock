#!/usr/bin/env bash
#
# Builds the release APK, checks it, and writes the release files to dist/.
#
# Called only by the release workflow (see docs/WORKFLOW.md), with CI=true. It needs these
# environment variables from the workflow: KEYSTORE_BASE64, KEYSTORE_PASS, KEY_ALIAS, KEY_PASS,
# and GH_TOKEN for the previous-release check. Nothing is committed or written to tracked files.
#
# Output in dist/:
#   DeskClock-<versionName>.apk
#   DeskClock-<versionName>.apk.sha256
#   build-info.json   (read by GKUI-Pack apps.yaml; keep the field names stable)
# and, outside dist/, build/release-notes.md for the release body.
# The tag v<versionName> is written to GITHUB_OUTPUT as "tag".

set -euo pipefail

readonly APP_NAME="DeskClock"
# The release applicationId. Must match build.gradle and the LineageOS built-in package.
readonly EXPECTED_APPLICATION_ID="com.android.deskclock"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly REPO_ROOT
readonly SIGNER_FILE="${REPO_ROOT}/release-signer.sha256"
readonly APK_IN="${REPO_ROOT}/build/outputs/apk/release/DeskClock-release.apk"
readonly DIST_DIR="${REPO_ROOT}/dist"
readonly NOTES_FILE="${REPO_ROOT}/build/release-notes.md"

die() {
    echo "::error::$*" >&2
    exit 1
}

# Progress goes to stderr: some functions print values on stdout and are captured with $(...).
log() {
    echo "[ci-build] $*" >&2
}

require_env() {
    local name="$1"
    if [[ -z "${!name:-}" ]]; then
        die "environment variable ${name} is not set"
    fi
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

for command in base64 find git gh jq sha256sum; do
    require_command "$command"
done

# Locates a build-tools binary. If the workflow pins PINNED_BUILD_TOOLS_VERSION, use that
# exact build-tools directory so newer preinstalled SDKs cannot change gate behavior; otherwise
# prefer PATH, then the newest build-tools under the SDK.
find_build_tool() {
    local name="$1"
    local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    if [[ -n "${PINNED_BUILD_TOOLS_VERSION:-}" ]]; then
        if [[ -z "${sdk}" ]]; then
            die "ANDROID_HOME is not set; cannot find pinned ${name}"
        fi
        local pinned="${sdk}/build-tools/${PINNED_BUILD_TOOLS_VERSION}/${name}"
        if [[ ! -x "${pinned}" ]]; then
            die "pinned ${name} not found at ${pinned}; install build-tools;${PINNED_BUILD_TOOLS_VERSION}"
        fi
        echo "${pinned}"
        return
    fi
    if command -v "${name}" >/dev/null 2>&1; then
        command -v "${name}"
        return
    fi
    if [[ -z "${sdk}" ]]; then
        die "ANDROID_HOME is not set; cannot find ${name}"
    fi
    local found
    found="$(find "${sdk}/build-tools" -mindepth 2 -maxdepth 2 -name "${name}" 2>/dev/null | sort -V | tail -n 1)"
    if [[ -z "${found}" ]]; then
        die "${name} not found under ${sdk}/build-tools; install a build-tools package"
    fi
    echo "${found}"
}

# Decodes the keystore into a private temporary file. The file is removed on exit.
# Global, so the EXIT trap can still see it after the function returns.
KEYSTORE_TMP=""

decode_keystore() {
    require_env KEYSTORE_BASE64
    umask 077
    KEYSTORE_TMP="$(mktemp "${RUNNER_TEMP:-/tmp}/release-keystore.XXXXXX")"
    trap 'rm -f "${KEYSTORE_TMP}"' EXIT
    if ! printf '%s' "${KEYSTORE_BASE64}" | base64 --decode >"${KEYSTORE_TMP}"; then
        die "KEYSTORE_BASE64 is not valid base64"
    fi
    if [[ ! -s "${KEYSTORE_TMP}" ]]; then
        die "decoded keystore is empty"
    fi
    export KEYSTORE_FILE="${KEYSTORE_TMP}"
}

# Reads a field from aapt2 badging output, first match only.
badging_field() {
    local badging="$1"
    local regex="$2"
    sed -n "${regex}" <<<"${badging}" | head -n 1
}

# Lower-case hex SHA-256 with spaces, colons and line breaks removed.
normalise_fingerprint() {
    tr -d ' :\r\n' | tr 'A-F' 'a-f'
}

# Prints the versionCode of the most recent release that has a build-info.json.
# Prints nothing when there is no earlier release, or when it has no build-info.json.
previous_version_code() {
    local tag
    if ! tag="$(gh release list --exclude-drafts --limit 1 --json tagName --jq '.[0].tagName // empty')"; then
        die "cannot list releases with gh (check GH_TOKEN)"
    fi
    if [[ -z "${tag}" ]]; then
        return
    fi
    local assets
    if ! assets="$(gh release view "${tag}" --json assets --jq '.assets[].name')"; then
        die "cannot read assets of release ${tag}"
    fi
    if ! grep -qx 'build-info.json' <<<"${assets}"; then
        log "previous release ${tag} has no build-info.json; versionCode check skipped"
        return
    fi
    local tmp_dir
    tmp_dir="$(mktemp -d "${RUNNER_TEMP:-/tmp}/prev-release.XXXXXX")"
    if ! gh release download "${tag}" --pattern build-info.json --dir "${tmp_dir}" --clobber >/dev/null; then
        die "cannot download build-info.json from release ${tag}"
    fi
    jq -r '.versionCode' "${tmp_dir}/build-info.json"
    rm -rf "${tmp_dir}"
}

main() {
    if [[ "${CI:-}" != "true" ]]; then
        die "ci-build.sh is for CI only (CI=true); local release builds use Gradle directly"
    fi

    # versionCode is the commit count: a shallow clone undercounts and must never release.
    if [[ "$(git -C "${REPO_ROOT}" rev-parse --is-shallow-repository)" == "true" ]]; then
        die "shallow clone detected: versionCode requires the full history (checkout with fetch-depth: 0)"
    fi

    require_env KEYSTORE_PASS
    require_env KEY_ALIAS
    require_env KEY_PASS
    local aapt2 apksigner
    aapt2="$(find_build_tool aapt2)"
    apksigner="$(find_build_tool apksigner)"

    decode_keystore

    log "building release APK"
    cd "${REPO_ROOT}"
    ./gradlew --no-daemon assembleRelease

    # The build must not have changed any tracked file. Build output is git-ignored.
    if [[ -n "$(git status --porcelain)" ]]; then
        git status --short
        die "the build changed tracked files; release refused"
    fi

    if [[ ! -f "${APK_IN}" ]]; then
        die "release APK not found at ${APK_IN}"
    fi

    # --- Gate 1: package name and debuggable flag -------------------------------------------
    local badging app_id version_code version_name
    badging="$("${aapt2}" dump badging "${APK_IN}")"
    app_id="$(badging_field "${badging}" "s/^package: name='\([^']*\)'.*/\1/p")"
    version_code="$(badging_field "${badging}" "s/^package: .* versionCode='\([^']*\)'.*/\1/p")"
    version_name="$(badging_field "${badging}" "s/^package: .* versionName='\([^']*\)'.*/\1/p")"

    if [[ "${app_id}" =~ \.(dev|debug)$ ]]; then
        die "applicationId ${app_id} carries a dev/debug suffix"
    fi
    if [[ "${app_id}" != "${EXPECTED_APPLICATION_ID}" ]]; then
        die "applicationId is ${app_id}, expected ${EXPECTED_APPLICATION_ID}"
    fi
    if [[ ! "${version_code}" =~ ^[0-9]+$ ]]; then
        die "versionCode is not a number: '${version_code}'"
    fi
    if [[ -z "${version_name}" ]]; then
        die "versionName is empty"
    fi

    local manifest
    manifest="$("${aapt2}" dump xmltree --file AndroidManifest.xml "${APK_IN}")"
    if grep -Eq 'android:debuggable\(0x[0-9a-f]+\)=\(type 0x[0-9a-f]+\)0xffffffff' <<<"${manifest}"; then
        die "release APK is debuggable"
    fi
    log "applicationId=${app_id} versionCode=${version_code} versionName=${version_name}"

    # --- Gate 2: signing certificate -------------------------------------------------------
    local signer_out signer_count fingerprint expected signer_digests
    if ! signer_out="$("${apksigner}" verify --print-certs "${APK_IN}")"; then
        die "apksigner verify failed: the APK is not correctly signed"
    fi
    # build-tools 37 renamed the signer lines (for example, "V2 Signer: certificate SHA-256 digest:").
    # Accept any prefix before "certificate SHA-256 digest:", normalize each digest, and count
    # distinct certificates. One key under multiple signature schemes is still one signer.
    signer_digests="$(printf '%s\n' "${signer_out}" | sed -n 's/.*certificate SHA-256 digest:[[:space:]]*//p' | tr -d ' :\r' | tr 'A-F' 'a-f' | grep -E '^[0-9a-f]{64}$' | sort -u || true)"
    signer_count="$(printf '%s\n' "${signer_digests}" | sed '/^$/d' | wc -l | tr -d '[:space:]')"
    if [[ "${signer_count}" == "0" ]]; then
        printf '%s\n' "${signer_out}" >&2
        die "could not read any certificate SHA-256 digest from apksigner output"
    fi
    if [[ "${signer_count}" != "1" ]]; then
        printf '%s\n' "${signer_out}" >&2
        die "expected exactly one signer, found ${signer_count} distinct certificate(s)"
    fi
    fingerprint="$(sed -n '1p' <<<"${signer_digests}")"
    if [[ ! "${fingerprint}" =~ ^[0-9a-f]{64}$ ]]; then
        die "could not read the signer SHA-256 from apksigner output"
    fi
    if [[ ! -s "${SIGNER_FILE}" ]]; then
        echo "release-signer.sha256 is missing. The owner must create it with this content:" >&2
        echo "${fingerprint}" >&2
        die "release-signer.sha256 not found in the repository"
    fi
    expected="$(grep -Ev '^[[:space:]]*(#|$)' "${SIGNER_FILE}" | head -n 1 | normalise_fingerprint || true)"
    if [[ ! "${expected}" =~ ^[0-9a-f]{64}$ ]]; then
        die "release-signer.sha256 must contain one SHA-256 fingerprint"
    fi
    if [[ "${fingerprint}" != "${expected}" ]]; then
        die "signer SHA-256 ${fingerprint} does not match release-signer.sha256 (${expected})"
    fi
    log "signer SHA-256 ${fingerprint} matches release-signer.sha256"

    # --- Gate 3: versionCode must increase over the previous release -----------------------
    local prev_code
    prev_code="$(previous_version_code)"
    if [[ -n "${prev_code}" ]]; then
        if [[ ! "${prev_code}" =~ ^[0-9]+$ ]]; then
            die "previous build-info.json has no numeric versionCode: '${prev_code}'"
        fi
        if (( version_code <= prev_code )); then
            die "versionCode ${version_code} is not greater than the previous release (${prev_code})"
        fi
        log "versionCode ${version_code} > previous ${prev_code}"
    fi

    local tag="v${version_name}"
    if gh release view "${tag}" >/dev/null 2>&1; then
        die "release ${tag} already exists"
    fi

    # --- Outputs ---------------------------------------------------------------------------
    local base="${APP_NAME}-${version_name}"
    local commit="${GITHUB_SHA:-$(git rev-parse HEAD)}"
    local repo="${GITHUB_REPOSITORY:-gx-bangsong/android_packages_apps_DeskClock}"
    local source_url="https://github.com/${repo}/tree/${commit}"

    rm -rf "${DIST_DIR}"
    mkdir -p "${DIST_DIR}"
    cp "${APK_IN}" "${DIST_DIR}/${base}.apk"
    (cd "${DIST_DIR}" && sha256sum "${base}.apk" >"${base}.apk.sha256")
    local apk_sha
    apk_sha="$(cut -d' ' -f1 "${DIST_DIR}/${base}.apk.sha256")"

    jq -n \
        --arg applicationId "${app_id}" \
        --argjson versionCode "${version_code}" \
        --arg versionName "${version_name}" \
        --arg signer_sha256 "${fingerprint}" \
        --arg apk_sha256 "${apk_sha}" \
        --arg apk "${base}.apk" \
        --arg commit "${commit}" \
        --arg source_url "${source_url}" \
        '{applicationId: $applicationId, versionCode: $versionCode, versionName: $versionName,
          signer_sha256: $signer_sha256, apk_sha256: $apk_sha256, apk: $apk,
          commit: $commit, source_url: $source_url}' \
        >"${DIST_DIR}/build-info.json"

    mkdir -p "$(dirname "${NOTES_FILE}")"
    cat >"${NOTES_FILE}" <<NOTES
Release build of ${APP_NAME} (DeskClock), signed with the release key.

- applicationId: \`${app_id}\`
- versionCode: ${version_code}
- versionName: ${version_name}
- signer SHA-256: \`${fingerprint}\`
- APK SHA-256: \`${apk_sha}\`
- Source code for this build: ${source_url}

This release is signed with a different key from the debug builds. Earlier debug builds
(\`${app_id}.dev\`) cannot be updated in place: uninstall them first. Uninstalling removes
their data.
NOTES

    if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
        printf 'tag=%s\n' "${tag}" >>"${GITHUB_OUTPUT}"
    fi
    log "wrote ${DIST_DIR}/${base}.apk, tag ${tag}"
}

main "$@"
