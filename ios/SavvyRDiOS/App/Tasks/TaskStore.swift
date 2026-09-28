import Foundation
import Combine

/// R&D 16. Deliberately minimal to-do model: the point is linking task state to
/// restriction state, not building a task manager.
struct FocusTask: Codable, Identifiable, Equatable {
    enum Status: String, Codable { case pending, active, completed, expired }
    var id: String = UUID().uuidString
    var title: String
    var durationMinutes: Int?    // nil = until completed (capped at 24 h)
    var status: Status = .pending
}

@MainActor
final class TaskStore: ObservableObject {
    @Published var tasks: [FocusTask] = [] { didSet { save() } }
    private let key = "tasks.v1"

    init() {
        if let data = AppGroup.defaults.data(forKey: key),
           let saved = try? JSONDecoder().decode([FocusTask].self, from: data) { tasks = saved }
    }

    func add(title: String, minutes: Int?) { tasks.append(FocusTask(title: title, durationMinutes: minutes)) }

    func set(_ id: String, _ status: FocusTask.Status) {
        guard let i = tasks.firstIndex(where: { $0.id == id }) else { return }
        tasks[i].status = status
    }

    private func save() {
        if let data = try? JSONEncoder().encode(tasks) { AppGroup.defaults.set(data, forKey: key) }
    }
}
