#!/usr/bin/env bash
set -euo pipefail

: "${YURI_CAPITAL_REPO_USERNAME:?set YURI_CAPITAL_REPO_USERNAME}"
: "${YURI_CAPITAL_REPO_PASSWORD:?set YURI_CAPITAL_REPO_PASSWORD}"

HOST="repo.yuri.capital"
KEEL_NPM="https://${HOST}/repository/keel-npm/"
VERSION="$(node -p "require('./packages/core/package.json').version")"
if [[ "${VERSION}" == *[Ss][Nn][Aa][Pp][Ss][Hh][Oo][Tt]* ]]; then
  EXTRA="https://${HOST}/repository/npm-snapshots/"
else
  EXTRA="https://${HOST}/repository/npm-releases/"
fi

AUTH="$(printf '%s:%s' "${YURI_CAPITAL_REPO_USERNAME}" "${YURI_CAPITAL_REPO_PASSWORD}" | openssl base64 -A)"

auth_registry() {
  local url="$1"
  local path="${url#https://}"
  path="${path#http://}"
  printf '@kolektiv:registry=%s\n' "${url}"
  printf '//%s:_auth=%s\n' "${path}" "${AUTH}"
  printf '//%s:always-auth=true\n' "${path}"
}

publish_to() {
  local url="$1"
  local npmrc
  npmrc="$(mktemp)"
  auth_registry "${url}" >"${npmrc}"
  echo "publishing @kolektiv/* to ${url}"
  NPM_CONFIG_USERCONFIG="${npmrc}" pnpm --filter "./packages/**" publish --no-git-checks --access public --registry "${url}"
  rm -f "${npmrc}"
}

publish_to "${KEEL_NPM}"
publish_to "${EXTRA}"
