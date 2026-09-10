#!/usr/bin/env bash
# Publish @kolektiv/* to the hosted Nexus repo (keel-npm).
# npm-releases / npm-snapshots are groups — read-only, PUT returns 404.
set -euo pipefail

: "${YURI_CAPITAL_REPO_USERNAME:?set YURI_CAPITAL_REPO_USERNAME}"
: "${YURI_CAPITAL_REPO_PASSWORD:?set YURI_CAPITAL_REPO_PASSWORD}"

HOST="repo.yuri.capital"
REGISTRY="https://${HOST}/repository/keel-npm/"
VERSION="$(node -p "require('./packages/core/package.json').version")"
PACKAGES=(@kolektiv/keel @kolektiv/keel-pack @kolektiv/keel-svelte)

AUTH="$(printf '%s:%s' "${YURI_CAPITAL_REPO_USERNAME}" "${YURI_CAPITAL_REPO_PASSWORD}" | openssl base64 -A)"
path="${REGISTRY#https://}"
path="${path#http://}"

NPMRC="$(mktemp)"
trap 'rm -f "${NPMRC}"' EXIT
{
  printf '@kolektiv:registry=%s\n' "${REGISTRY}"
  printf '//%s:_auth=%s\n' "${path}" "${AUTH}"
  printf '//%s:always-auth=true\n' "${path}"
} >"${NPMRC}"

already_published() {
  local pkg="$1"
  NPM_CONFIG_USERCONFIG="${NPMRC}" npm view "${pkg}@${VERSION}" --registry "${REGISTRY}" >/dev/null 2>&1
}

echo "publishing @kolektiv/*@${VERSION} to ${REGISTRY}"
for pkg in "${PACKAGES[@]}"; do
  if already_published "${pkg}"; then
    echo "already published ${pkg}@${VERSION}, skipping"
    continue
  fi
  NPM_CONFIG_USERCONFIG="${NPMRC}" pnpm --filter "${pkg}" publish --no-git-checks --access public --registry "${REGISTRY}"
done
