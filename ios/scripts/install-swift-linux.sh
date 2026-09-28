#!/usr/bin/env bash
# Installs a Swift 6.0.3 toolchain on Ubuntu 24.04 without root changes, from the
# Ubuntu archive packages (download.swift.org may be blocked in CI sandboxes).
# Usage: source ios/scripts/install-swift-linux.sh   (exports PATH / LD_LIBRARY_PATH)
set -e
DEST=${SWIFT_LOCAL:-$HOME/.local/swift-6.0.3}
if [ ! -x "$DEST/usr/libexec/swift/bin/swift" ]; then
  mkdir -p "$DEST/debs"
  base=http://archive.ubuntu.com/ubuntu/pool
  for p in universe/s/swiftlang/swiftlang_6.0.3-2build1_amd64.deb universe/s/swiftlang/libswiftlang_6.0.3-2build1_amd64.deb \
           main/libx/libxml2/libxml2-16_2.15.2+dfsg-0.1_amd64.deb main/p/python3.13/libpython3.13_3.13.3-1ubuntu0.5_amd64.deb; do
    curl -sSfL -o "$DEST/debs/$(basename "$p")" "$base/$p"
  done
  for d in "$DEST"/debs/*.deb; do dpkg-deb -x "$d" "$DEST"; done
fi
export PATH="$DEST/usr/libexec/swift/bin:$PATH"
export LD_LIBRARY_PATH="$DEST/usr/lib/x86_64-linux-gnu:$DEST/usr/libexec/swift/lib:${LD_LIBRARY_PATH:-}"
swift --version
