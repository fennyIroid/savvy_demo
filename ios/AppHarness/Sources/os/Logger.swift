// Harness fake of os.Logger. Not Apple code.
public struct OSLogPrivacy: Sendable { public static let `public` = OSLogPrivacy() }
public struct OSLogMessage: ExpressibleByStringInterpolation {
    public struct StringInterpolation: StringInterpolationProtocol {
        var text = ""
        public init(literalCapacity: Int, interpolationCount: Int) {}
        public mutating func appendLiteral(_ literal: String) { text += literal }
        public mutating func appendInterpolation(_ value: String, privacy: OSLogPrivacy) { text += value }
    }
    let text: String
    public init(stringLiteral value: String) { text = value }
    public init(stringInterpolation: StringInterpolation) { text = stringInterpolation.text }
}
public struct Logger {
    public init(subsystem: String, category: String) {}
    public func log(_ message: OSLogMessage) {}
}
