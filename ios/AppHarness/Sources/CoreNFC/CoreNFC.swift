// Harness FAKE of the CoreNFC members the POC uses (card reading itself needs an iPhone).
import Foundation

public enum NFCTypeNameFormat { case empty, nfcWellKnown, media, absoluteURI, nfcExternal, unknown, unchanged }
open class NFCNDEFPayload {
    public init() {}
    open var typeNameFormat: NFCTypeNameFormat = .empty
    open func wellKnownTypeURIPayload() -> URL? { nil }
}
open class NFCNDEFMessage { public init() {}; open var records: [NFCNDEFPayload] = [] }
public protocol NFCNDEFTag { func readNDEF(completionHandler: @escaping (NFCNDEFMessage?, Error?) -> Void) }
public enum NFCMiFareFamily: Int { case unknown = 1, ultralight, plus, desfire }
public protocol NFCMiFareTag: NFCNDEFTag { var identifier: Data { get }; var mifareFamily: NFCMiFareFamily { get } }
open class NFCISO7816APDU { public let data: Data; public init?(data: Data) { self.data = data } }
public protocol NFCISO7816Tag: NFCNDEFTag {
    var identifier: Data { get }
    func sendCommand(apdu: NFCISO7816APDU, completionHandler: @escaping (Data, UInt8, UInt8, Error?) -> Void)
}
public protocol NFCISO15693Tag: NFCNDEFTag { var identifier: Data { get } }
public protocol NFCFeliCaTag: NFCNDEFTag { var currentIDm: Data { get } }
public enum NFCTag { case feliCa(NFCFeliCaTag), iso7816(NFCISO7816Tag), iso15693(NFCISO15693Tag), miFare(NFCMiFareTag) }
public struct NFCReaderError: Error {
    public enum Code { case readerSessionInvalidationErrorUserCanceled, readerSessionInvalidationErrorFirstNDEFTagRead, readerSessionInvalidationErrorSessionTimeout }
    public var code: Code
}
open class NFCReaderSession: NSObject {
    open class var readingAvailable: Bool { false }
    open var alertMessage = ""
    open func begin() {}
    open func invalidate() {}
    open func invalidate(errorMessage: String) {}
}
public protocol NFCTagReaderSessionDelegate: NSObjectProtocol {
    func tagReaderSessionDidBecomeActive(_ session: NFCTagReaderSession)
    func tagReaderSession(_ session: NFCTagReaderSession, didInvalidateWithError error: Error)
    func tagReaderSession(_ session: NFCTagReaderSession, didDetect tags: [NFCTag])
}
open class NFCTagReaderSession: NFCReaderSession {
    public struct PollingOption: OptionSet {
        public let rawValue: Int
        public init(rawValue: Int) { self.rawValue = rawValue }
        public static let iso14443 = PollingOption(rawValue: 1)
        public static let iso15693 = PollingOption(rawValue: 2)
        public static let iso18092 = PollingOption(rawValue: 4)
    }
    public init?(pollingOption: PollingOption, delegate: NFCTagReaderSessionDelegate, queue: DispatchQueue? = nil) {}
    open func connect(to tag: NFCTag, completionHandler: @escaping (Error?) -> Void) {}
}
public protocol NFCNDEFReaderSessionDelegate: NSObjectProtocol {
    func readerSession(_ session: NFCNDEFReaderSession, didInvalidateWithError error: Error)
    func readerSession(_ session: NFCNDEFReaderSession, didDetectNDEFs messages: [NFCNDEFMessage])
}
open class NFCNDEFReaderSession: NFCReaderSession {
    public init(delegate: NFCNDEFReaderSessionDelegate, queue: DispatchQueue?, invalidateAfterFirstRead: Bool) {}
}
/// NSUserActivity is Apple Foundation; swift-corelibs-foundation does not have it.
open class NSUserActivity {
    public init() {}
    open var webpageURL: URL?
    open var ndefMessagePayload = NFCNDEFMessage()
}
