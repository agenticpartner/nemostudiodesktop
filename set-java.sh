#!/usr/bin/env sh
# Sets JAVA_HOME for this project using the version in .java-version.
# Source this before running Maven:  source ./set-java.sh
# Or use ./run.sh which does this automatically.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
VERSION_FILE="$SCRIPT_DIR/.java-version"

if [ ! -r "$VERSION_FILE" ]; then
  return 0 2>/dev/null || true
  exit 0
fi

JAVA_VERSION=$(cat "$VERSION_FILE" | tr -d '\r\n' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')

if [ -z "$JAVA_VERSION" ]; then
  return 0 2>/dev/null || true
  exit 0
fi

# macOS: use java_home to resolve version to path
if [ -x "/usr/libexec/java_home" ] 2>/dev/null; then
  JAVA_HOME_CANDIDATE=$(/usr/libexec/java_home -v "$JAVA_VERSION" 2>/dev/null)
  if [ -n "$JAVA_HOME_CANDIDATE" ]; then
    export JAVA_HOME="$JAVA_HOME_CANDIDATE"
    return 0 2>/dev/null || true
    exit 0
  fi
fi

# Linux: optional config file with explicit path (e.g. /usr/lib/jvm/java-25-openjdk)
if [ -r "$SCRIPT_DIR/config/java-home" ]; then
  export JAVA_HOME=$(cat "$SCRIPT_DIR/config/java-home" | tr -d '\r\n')
  return 0 2>/dev/null || true
  exit 0
fi

# If we couldn't set it, leave JAVA_HOME unchanged (user may have set it already)
return 0 2>/dev/null || true
exit 0
