#!/usr/bin/env bash
# Publish Keel Maven artifacts to Nexus, tolerating assets that are already
# present: hosted repos reject redeploys (409) and snapshot repos reject
# versions that do not end in -SNAPSHOT (400). Any other failure fails the job.
set -uo pipefail

: "${YURI_CAPITAL_REPO_USERNAME:?set YURI_CAPITAL_REPO_USERNAME}"
: "${YURI_CAPITAL_REPO_PASSWORD:?set YURI_CAPITAL_REPO_PASSWORD}"

HOST="repo.yuri.capital"
GROUP_PATH="dev/kolektiv/keel"
MODULES=(core ktor)
AUTH=(-u "${YURI_CAPITAL_REPO_USERNAME}:${YURI_CAPITAL_REPO_PASSWORD}")

VERSION="$(grep -E '^version=' gradle.properties | head -n1 | cut -d= -f2 | tr -d '[:space:]')"
if [ -z "${VERSION}" ]; then
  echo "::error::could not read version from gradle.properties"
  exit 1
fi

# Maven snapshots are versions that end exactly in -SNAPSHOT.
case "${VERSION}" in
  *-SNAPSHOT) TARGET_REPO="maven-snapshots" ;;
  *) TARGET_REPO="maven-releases" ;;
esac

artifact_url() {
  printf 'https://%s/repository/%s/%s/%s/%s/%s-%s.pom' \
    "${HOST}" "$1" "${GROUP_PATH}" "$2" "${VERSION}" "$2" "${VERSION}"
}

url_exists() {
  local code
  code="$(curl -sS -o /dev/null -w '%{http_code}' "${AUTH[@]}" "$1")"
  [ "${code}" = "200" ]
}

published=1
for module in "${MODULES[@]}"; do
  url_exists "$(artifact_url "${TARGET_REPO}" "${module}")" || published=0
done
if [ "${published}" -eq 1 ]; then
  echo "::warning::dev.kolektiv.keel:${VERSION} is already published in ${TARGET_REPO}; skipping Maven publish."
  exit 0
fi

LOG="$(mktemp)"
trap 'rm -f "${LOG}"' EXIT

echo "publishing dev.kolektiv.keel:${VERSION} to keel-maven and ${TARGET_REPO}"
./gradlew :lib:publish :ktor:publish --continue 2>&1 | tee "${LOG}"
status="${PIPESTATUS[0]}"

if [ "${status}" -eq 0 ]; then
  echo "Maven publish succeeded"
  exit 0
fi

failures="$(grep -oE "Could not PUT '[^']+'. Received status code [0-9]+ from server: [A-Za-z ]+" "${LOG}" | sort -u)"
if [ -z "${failures}" ]; then
  echo "::error::Maven publish failed for a reason other than an existing or immutable asset"
  exit 1
fi

tolerated=0
while IFS= read -r failure; do
  url="${failure#Could not PUT \'}"; url="${url%%\'*}"
  code="${failure##*status code }"; code="${code%% *}"
  if [ "${code}" = "409" ]; then
    echo "::warning::ignoring conflict (asset already exists): ${url}"
    tolerated=$((tolerated + 1))
    continue
  fi
  if [ "${code}" = "400" ]; then
    mirror="$(printf '%s' "${url}" | sed -E 's#(/repository/)[^/]+/#\1keel-maven/#')"
    if [ "${mirror}" != "${url}" ] && url_exists "${mirror}"; then
      echo "::warning::ignoring 400 (version rejected by target repo, asset already in keel-maven): ${url}"
      tolerated=$((tolerated + 1))
      continue
    fi
  fi
  echo "::error::untolerated publish failure: ${failure}"
  exit 1
done <<< "${failures}"

echo "::warning::Maven publish failed only on existing/immutable assets (${tolerated} conflict(s)); continuing."
exit 0
