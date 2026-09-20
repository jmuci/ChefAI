#!/usr/bin/env bash
# Vendor Lucide icons as Android VectorDrawables into res/drawable.
#
# Usage:
#   tools/lucide-import.sh <icon-name>[:filled][:mirror] [<icon-name>...]
#
# Examples:
#   tools/lucide-import.sh search chef-hat
#   tools/lucide-import.sh bookmark bookmark:filled
#   tools/lucide-import.sh arrow-left:mirror
#
# Each Lucide icon name is downloaded from the pinned release below and
# converted with lucide_svg_to_vector.py into
# res/drawable/ic_lucide_<name_with_underscores>[_filled].xml.
#
# Bump LUCIDE_VERSION to pull a newer icon set; every icon already imported
# should be re-run against the new pin so drift is visible in the diff.
set -euo pipefail

LUCIDE_VERSION="1.47.0"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DRAWABLE_DIR="$REPO_ROOT/app/src/main/res/drawable"
CONVERTER="$SCRIPT_DIR/lucide_svg_to_vector.py"

if [ "$#" -eq 0 ]; then
  sed -n '2,13p' "${BASH_SOURCE[0]}"
  exit 1
fi

command -v curl >/dev/null || { echo "error: curl is required" >&2; exit 1; }
command -v python3 >/dev/null || { echo "error: python3 is required" >&2; exit 1; }

for spec in "$@"; do
  icon_name="${spec%%:*}"
  flags=""
  if [[ "$spec" == *:* ]]; then
    flags="${spec#*:}"
  fi

  converter_args=()
  suffix=""
  if [[ ",$flags," == *,filled,* ]]; then
    converter_args+=(--filled)
    suffix="_filled"
  fi
  if [[ ",$flags," == *,mirror,* ]]; then
    converter_args+=(--mirror)
  fi

  res_name="ic_lucide_$(echo "$icon_name" | tr '-' '_')${suffix}"
  out_file="$DRAWABLE_DIR/${res_name}.xml"
  svg_url="https://raw.githubusercontent.com/lucide-icons/lucide/${LUCIDE_VERSION}/icons/${icon_name}.svg"

  echo "-> ${icon_name} (${flags:-outline}) -> res/drawable/${res_name}.xml"

  svg_content="$(curl -sL -f "$svg_url")" || {
    echo "   error: '${icon_name}' not found in lucide-icons/lucide@${LUCIDE_VERSION} (${svg_url})" >&2
    exit 1
  }

  echo "$svg_content" | python3 "$CONVERTER" ${converter_args[@]+"${converter_args[@]}"} > "$out_file" || {
    rm -f "$out_file"
    echo "   error: conversion failed for '${icon_name}'" >&2
    exit 1
  }
done

echo "Done. Pinned Lucide version: ${LUCIDE_VERSION}"
