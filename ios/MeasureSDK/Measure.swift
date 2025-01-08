//
//  Measure.swift
//  Measure
//
//  Created by Adwin Ross on 12/08/24.
//

import Foundation

/// `Measure` is a singleton class responsible for managing the initialization and configuration of the Measure SDK.
///
/// The `Measure` class provides a shared instance that is used to initialize and configure the SDK.
/// This class ensures that the SDK is initialized only once, and offers thread-safe access to the shared instance.
///
/// - Note: It is recommended to initialize the SDK as early as possible during the application startup to ensure
/// that exceptions and other events are captured promptly.
///
@objc public final class Measure: NSObject {
    /// The shared instance of `Measure`.
    ///
    /// Use this property to access the singleton instance of the `Measure` class. The shared instance is lazily
    /// instantiated the first time it is accessed, ensuring that the SDK is initialized only once.
    ///
    /// - Example:
    ///   - Swift:
    ///   ```swift
    ///   let clientInfo = ClientInfo(apiKey: "apiKey", apiUrl: "apiUrl")
    ///   Measure.shared.initialize(with: clientInfo)
    ///   ```
    ///   - Objective-C:
    ///   ```objc
    ///   [[Measure shared] initializeWith:clientInfo config:config];
    ///   ```
    @objc public static let shared: Measure = {
        let instance = Measure()
        return instance
    }()
    private var measureInitializerLock = NSLock()
    private var measureInternal: MeasureInternal?
    var meaureInitializerInternal: MeasureInitializer?

    // Private initializer to ensure the singleton pattern
    private override init() {
        super.init()
    }

    /// Initializes the Measure SDK. The SDK must be initialized before using any of the other methods.
    ///
    /// It is recommended to initialize the SDK as early as possible in the application startup so that exceptions and other events can be captured as early as possible.
    ///
    /// An optional `MeasureConfig` can be passed to configure the SDK. If not provided, the SDK will use the default configuration.
    ///
    /// Initializing the SDK multiple times will have no effect.
    /// - Parameter config: The configuration for the Measure SDK.
    /// - Parameter client: `ClientInfo` object consisting the api-key and api-url
    ///
    /// - Example:
    ///   - Swift:
    ///   ```swift
    ///   let config = BaseMeasureConfig()
    ///   let clientInfo = ClientInfo(apiKey: "<apiKey>", apiUrl: "<apiUrl>")
    ///   Measure.shared.initialize(with: clientInfo, config: config)
    ///   ```
    ///   - Objective-C:
    ///   ```objc
    ///   BaseMeasureConfig *config = [[BaseMeasureConfig alloc] init];
    ///   ClientInfo *clientInfo = [[ClientInfo alloc] initWithApiKey:@"<apiKey>" apiUrl:@"<apiUrl>"];
    ///   [[Measure shared] initializeWith:clientInfo config:config];
    ///   ```
    @objc public func initialize(with client: ClientInfo, config: BaseMeasureConfig? = nil) {
        measureInitializerLock.lock()
        defer { measureInitializerLock.unlock() }

        // Ensure initialization is done only once
        guard measureInternal == nil else { return }

        SignPost.trace(label: "Measure Initialisation") {
            if let meaureInitializer = self.meaureInitializerInternal {
                measureInternal = MeasureInternal(meaureInitializer)
                meaureInitializer.logger.log(level: .info, message: "SDK enabled in testing mode.", error: nil, data: nil)
            } else {
                let meaureInitializer = BaseMeasureInitializer(config: config ?? BaseMeasureConfig(),
                                                               client: client)
                measureInternal = MeasureInternal(meaureInitializer)
            }
        }
    }

    /// Returns the session ID for the current session, or nil if the SDK has not been initialized.
    ///
    /// A session represents a continuous period of activity in the app. A new session begins when the app is launched for the first time, or when there's been no activity for a 20-minute period.
    /// A single session can continue across multiple app background and foreground events; brief interruptions will not cause a new session to be created.
    /// - Returns: The session ID if the SDK is initialized, or nil otherwise.
    func getSessionId() -> String? {
        guard let sessionId = measureInternal?.sessionManager.sessionId else { return nil }

        return sessionId
    }

    /// Tracks an event with optional timestamp.
    ///
    /// Usage Notes:
    /// - Event names should be clear and consistent to aid in dashboard searches
    ///
    ///   /// - Example:
    ///   ```swift
    ///   Measure.shared.trackEvent(name: "event-name", attributes:["user_name": .string("Alice")], timestamp: nil)
    ///   ```
    /// - Parameters:
    ///   - name: Name of the event (max 64 characters)
    ///   - attributes: Key-value pairs providing additional context
    ///   - timestamp: Optional timestamp for the event, defaults to current time
    ///
    public func trackEvent(name: String, attributes: [String: AttributeValue], timestamp: Int64?) {
        guard let customEventCollector = measureInternal?.customEventCollector else { return }

        customEventCollector.trackEvent(name: name, attributes: attributes, timestamp: timestamp)
    }

    /// Tracks an event with optional timestamp.
    ///
    /// Usage Notes:
    /// - Event names should be clear and consistent to aid in dashboard searches
    ///
    ///   /// - Example:
    ///   ```objc
    ///   [[Measure shared] trackEvent:@"event-name" attributes:@{@"user_name": @"Alice"} timestamp:nil];
    ///   ```
    /// - Parameters:
    ///   - name: Name of the event (max 64 characters)
    ///   - attributes: Key-value pairs providing additional context
    ///   - timestamp: Optional timestamp for the event, defaults to current time
    @objc public func trackEvent(_ name: String, attributes: [String: Any], timestamp: NSNumber?) {
        guard let customEventCollector = measureInternal?.customEventCollector,
              let logger = measureInternal?.logger else { return }
        var transformedAttributes: [String: AttributeValue] = [:]

        for (key, value) in attributes {
            if let stringVal = value as? String {
                transformedAttributes[key] = .string(stringVal)
            } else if let boolVal = value as? Bool {
                transformedAttributes[key] = .boolean(boolVal)
            } else if let intVal = value as? Int {
                transformedAttributes[key] = .int(intVal)
            } else if let longVal = value as? Int64 {
                transformedAttributes[key] = .long(longVal)
            } else if let floatVal = value as? Float {
                transformedAttributes[key] = .float(floatVal)
            } else if let doubleVal = value as? Double {
                transformedAttributes[key] = .double(doubleVal)
            } else {
                #if DEBUG
                fatalError("Attribute value can only be a string, boolean, integer, or double.")
                #else
                logger.log(level: .fatal, message: "Attribute value can only be a string, boolean, integer, or double.", error: nil, data: nil)
                #endif
            }
        }

        customEventCollector.trackEvent(name: name, attributes: transformedAttributes, timestamp: timestamp?.int64Value)
    }
}
