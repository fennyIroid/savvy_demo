// swift-tools-version:5.9
// iOS app-logic harness for Linux (and macOS without a device).
//
// Builds the NON-UI iOS POC sources (symlinked from ../SavvyRDiOS) against
// functional FAKES of Apple frameworks that record state (for example which apps a
// ManagedSettingsStore currently shields), and runs end-to-end XCTests against the
// real Node backend. The fakes are our model of Apple's documented behaviour, not
// Apple code: this proves Savvy's own logic, backend protocol and crypto, not the
// behaviour of iOS itself (that needs the device tests in docs/IOS_POC_RESULTS.md).
import PackageDescription

let fakes = ["ManagedSettings", "FamilyControls", "DeviceActivity", "CoreNFC", "Combine", "UserNotifications", "os"]

let package = Package(
    name: "AppHarness",
    platforms: [.macOS(.v13)],
    dependencies: [.package(path: "../SavvyCore")],
    targets: [
        .target(name: "Combine"),
        .target(name: "os"),
        .target(name: "ManagedSettings"),
        .target(name: "FamilyControls", dependencies: ["ManagedSettings", "Combine"]),
        .target(name: "DeviceActivity"),
        .target(name: "CoreNFC"),
        .target(name: "UserNotifications"),
        .target(name: "SavvyApp", dependencies: fakes.map { .target(name: $0) } + [.product(name: "SavvyCore", package: "SavvyCore")]),
        .testTarget(name: "AppHarnessTests", dependencies: ["SavvyApp", "SavvyCore"] + fakes.map { .target(name: $0) }),
    ]
)
