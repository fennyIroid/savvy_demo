import DeviceActivity
import SwiftUI

/// DeviceActivityReport extension. Screen Time usage data is only available
/// inside this extension, which renders SwiftUI into the host app. The extension
/// is sandboxed: it cannot send the data to the network or back to the app.
/// So Savvy can SHOW a screen-time summary but cannot store or upload it.
@main
struct SavvyReportExtension: DeviceActivityReportExtension {
    var body: some DeviceActivityReportScene {
        TotalActivityReport { summary in
            TotalActivityView(summary: summary)
        }
    }
}

extension DeviceActivityReport.Context {
    static let totalActivity = Self("Total Activity")
}

struct ActivitySummary {
    var total: TimeInterval
    var topApps: [(name: String, duration: TimeInterval)]
    var pickups: Int
}

struct TotalActivityReport: DeviceActivityReportScene {
    let context: DeviceActivityReport.Context = .totalActivity
    let content: (ActivitySummary) -> TotalActivityView

    func makeConfiguration(representing data: DeviceActivityResults<DeviceActivityData>) async -> ActivitySummary {
        var total: TimeInterval = 0
        var perApp: [String: TimeInterval] = [:]
        var pickups = 0
        for await deviceData in data {
            for await segment in deviceData.activitySegments {
                total += segment.totalActivityDuration
                for await category in segment.categories {
                    for await app in category.applications {
                        let name = app.application.localizedDisplayName ?? "App"
                        perApp[name, default: 0] += app.totalActivityDuration
                        pickups += app.numberOfPickups
                    }
                }
            }
        }
        let top = perApp.sorted { $0.value > $1.value }.prefix(5).map { (name: $0.key, duration: $0.value) }
        return ActivitySummary(total: total, topApps: top, pickups: pickups)
    }
}

struct TotalActivityView: View {
    let summary: ActivitySummary

    private func format(_ t: TimeInterval) -> String {
        let f = DateComponentsFormatter()
        f.allowedUnits = [.hour, .minute]
        f.unitsStyle = .abbreviated
        return f.string(from: t) ?? "-"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Screen time today").font(.headline)
            Text(format(summary.total)).font(.largeTitle.bold())
            Text("Pickups: \(summary.pickups)").font(.subheadline)
            ForEach(summary.topApps, id: \.name) { item in
                HStack { Text(item.name); Spacer(); Text(format(item.duration)) }
            }
        }
        .padding()
    }
}
