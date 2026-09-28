#!/usr/bin/env bash
# Linux / CI checks for the iOS POC (needs a Swift 6 toolchain, see install-swift-linux.sh).
#  1. SavvyCore unit tests.
#  2. AppHarness: builds the non-UI app + extension sources against functional fakes of
#     the Apple frameworks and runs end-to-end tests against the real backend.
#  3. Syntax check of every Swift file (SwiftUI / UIKit / AppIntents files need Xcode).
set -euo pipefail
cd "$(dirname "$0")/.."
(cd SavvyCore && swift test)
(cd AppHarness && swift test)
for f in $(find SavvyRDiOS -name '*.swift'); do swiftc -parse "$f"; done
echo "OK: SavvyCore tests, AppHarness end-to-end tests, syntax check"
