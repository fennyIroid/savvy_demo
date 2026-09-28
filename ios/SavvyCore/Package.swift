// swift-tools-version:5.9
// Platform-neutral Savvy logic shared by the iOS app and its extensions.
// Builds and tests on Linux too (CryptoKit is replaced by swift-crypto there),
// so the logic is verified without a Mac: `swift test`.
import PackageDescription

let package = Package(
    name: "SavvyCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [.library(name: "SavvyCore", targets: ["SavvyCore"])],
    dependencies: [
        .package(url: "https://github.com/apple/swift-crypto.git", "3.0.0"..<"4.0.0"),
    ],
    targets: [
        .target(
            name: "SavvyCore",
            dependencies: [
                // Only Linux needs swift-crypto; Apple platforms use CryptoKit.
                .product(name: "Crypto", package: "swift-crypto", condition: .when(platforms: [.linux])),
            ]
        ),
        .testTarget(
            name: "SavvyCoreTests",
            dependencies: ["SavvyCore"],
            resources: [.copy("fixtures.json")]
        ),
    ]
)
