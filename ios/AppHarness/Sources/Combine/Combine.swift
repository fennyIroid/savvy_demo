// Harness fake of the Combine members the POC uses. Not Apple code.
public protocol ObservableObject: AnyObject {}
public final class AnyCancellable { public init() {} }
public struct PublisherStub<Output> {
    public func receive<S>(on scheduler: S) -> PublisherStub<Output> { self }
    public func sink(receiveValue: @escaping (Output) -> Void) -> AnyCancellable { AnyCancellable() }
}
@propertyWrapper public struct Published<Value> {
    public var wrappedValue: Value
    public init(wrappedValue: Value) { self.wrappedValue = wrappedValue }
    public var projectedValue: PublisherStub<Value> { PublisherStub() }
}
