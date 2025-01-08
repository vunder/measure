//
//  EventStore.swift
//  MeasureSDK
//
//  Created by Adwin Ross on 24/09/24.
//

import CoreData
import Foundation

protocol EventStore {
    func insertEvent(event: EventEntity)
    func getEvents(eventIds: [String]) -> [EventEntity]?
    func getEventsForSessions(sessions: [String]) -> [EventEntity]?
    func deleteEvents(eventIds: [String])
    func getAllEvents() -> [EventEntity]?
    func getUnBatchedEventsWithAttachmentSize(eventCount: Number, ascending: Bool, sessionId: String?) -> [String: Number]
    func updateBatchId(_ batchId: String, for events: [String])
}

final class BaseEventStore: EventStore {
    private let coreDataManager: CoreDataManager
    private let logger: Logger

    init(coreDataManager: CoreDataManager, logger: Logger) {
        self.coreDataManager = coreDataManager
        self.logger = logger
    }

    func insertEvent(event: EventEntity) {
        let context = coreDataManager.backgroundContext
        context.perform { [weak self] in
            let eventOb = EventOb(context: context)

            eventOb.id = event.id
            eventOb.sessionId = event.sessionId
            eventOb.timestamp = event.timestamp
            eventOb.type = event.type
            eventOb.userTriggered = event.userTriggered
            eventOb.exception = event.exception
            eventOb.attributes = event.attributes
            eventOb.attachments = event.attachments
            eventOb.gestureClick = event.gestureClick
            eventOb.gestureLongClick = event.gestureLongClick
            eventOb.gestureScroll = event.gestureScroll
            eventOb.lifecycleApp = event.lifecycleApp
            eventOb.lifecycleViewController = event.lifecycleViewController
            eventOb.lifecycleSwiftUI = event.lifecycleSwiftUI
            eventOb.cpuUsage = event.cpuUsage
            eventOb.memoryUsage = event.memoryUsage
            eventOb.coldLaunch = event.coldLaunch
            eventOb.warmLaunch = event.warmLaunch
            eventOb.hotLaunch = event.hotLaunch
            eventOb.http = event.http

            do {
                try context.saveIfNeeded()
            } catch {
                guard let self = self else { return }
                self.logger.internalLog(level: .error, message: "Failed to save session: \(event.id)", error: error, data: nil)
            }
        }
    }

    func getEvents(eventIds: [String]) -> [EventEntity]? {
        let context = coreDataManager.backgroundContext
        let fetchRequest: NSFetchRequest<EventOb> = EventOb.fetchRequest()
        fetchRequest.fetchLimit = eventIds.count
        fetchRequest.predicate = NSPredicate(format: "id IN %@", eventIds)

        var events: [EventEntity]?
        context.performAndWait { [weak self] in
            do {
                let result = try context.fetch(fetchRequest)
                events = result.map { eventOb in
                    EventEntity(id: eventOb.id ?? "",
                                sessionId: eventOb.sessionId ?? "",
                                timestamp: eventOb.timestamp ?? "",
                                type: eventOb.type ?? "",
                                exception: eventOb.exception,
                                attachments: eventOb.attachments,
                                attributes: eventOb.attributes,
                                gestureClick: eventOb.gestureClick,
                                gestureLongClick: eventOb.gestureLongClick,
                                gestureScroll: eventOb.gestureScroll,
                                userTriggered: eventOb.userTriggered,
                                attachmentSize: eventOb.attachmentSize,
                                timestampInMillis: eventOb.timestampInMillis,
                                batchId: eventOb.batchId,
                                lifecycleApp: eventOb.lifecycleApp,
                                lifecycleViewController: eventOb.lifecycleViewController,
                                lifecycleSwiftUI: eventOb.lifecycleSwiftUI,
                                cpuUsage: eventOb.cpuUsage,
                                memoryUsage: eventOb.memoryUsage,
                                coldLaunch: eventOb.coldLaunch,
                                warmLaunch: eventOb.warmLaunch,
                                hotLaunch: eventOb.hotLaunch,
                                http: eventOb.http)
                }
            } catch {
                guard let self = self else { return }
                self.logger.internalLog(level: .error, message: "Failed to fetch events by IDs.", error: error, data: nil)
            }
        }
        return events
    }

    func getEventsForSessions(sessions: [String]) -> [EventEntity]? {
        let context = coreDataManager.backgroundContext
        let fetchRequest: NSFetchRequest<EventOb> = EventOb.fetchRequest()
        fetchRequest.predicate = NSPredicate(format: "sessionId IN %@", sessions)

        var events: [EventEntity]?
        context.performAndWait { [weak self] in
            do {
                let result = try context.fetch(fetchRequest)
                events = result.map { eventOb in
                    EventEntity(id: eventOb.id ?? "",
                                sessionId: eventOb.sessionId ?? "",
                                timestamp: eventOb.timestamp ?? "",
                                type: eventOb.type ?? "",
                                exception: eventOb.exception,
                                attachments: eventOb.attachments,
                                attributes: eventOb.attributes,
                                gestureClick: eventOb.gestureClick,
                                gestureLongClick: eventOb.gestureLongClick,
                                gestureScroll: eventOb.gestureScroll,
                                userTriggered: eventOb.userTriggered,
                                attachmentSize: eventOb.attachmentSize,
                                timestampInMillis: eventOb.timestampInMillis,
                                batchId: eventOb.batchId,
                                lifecycleApp: eventOb.lifecycleApp,
                                lifecycleViewController: eventOb.lifecycleViewController,
                                lifecycleSwiftUI: eventOb.lifecycleSwiftUI,
                                cpuUsage: eventOb.cpuUsage,
                                memoryUsage: eventOb.memoryUsage,
                                coldLaunch: eventOb.coldLaunch,
                                warmLaunch: eventOb.warmLaunch,
                                hotLaunch: eventOb.hotLaunch,
                                http: eventOb.http)
                }
            } catch {
                guard let self = self else { return }
                self.logger.internalLog(level: .error, message: "Failed to fetch events by session IDs.", error: error, data: nil)
            }
        }
        return events
    }

    func deleteEvents(eventIds: [String]) {
        let context = coreDataManager.backgroundContext
        let fetchRequest: NSFetchRequest<EventOb> = EventOb.fetchRequest()
        fetchRequest.fetchLimit = eventIds.count
        fetchRequest.predicate = NSPredicate(format: "id IN %@", eventIds)

        context.perform { [weak self] in
            do {
                let events = try context.fetch(fetchRequest)
                for event in events {
                    context.delete(event)
                }
                try context.saveIfNeeded()
            } catch {
                guard let self = self else { return }
                self.logger.internalLog(level: .error, message: "Failed to delete events by IDs: \(eventIds.joined(separator: ","))", error: error, data: nil)
            }
        }
    }

    func getAllEvents() -> [EventEntity]? {
        let context = coreDataManager.backgroundContext
        let fetchRequest: NSFetchRequest<EventOb> = EventOb.fetchRequest()

        var events = [EventEntity]()
        context.performAndWait { [weak self] in
            do {
                let result = try context.fetch(fetchRequest)
                for eventOb in result {
                    events.append(EventEntity(id: eventOb.id ?? "",
                                              sessionId: eventOb.sessionId ?? "",
                                              timestamp: eventOb.timestamp ?? "",
                                              type: eventOb.type ?? "",
                                              exception: eventOb.exception,
                                              attachments: eventOb.attachments,
                                              attributes: eventOb.attributes,
                                              gestureClick: eventOb.gestureClick,
                                              gestureLongClick: eventOb.gestureLongClick,
                                              gestureScroll: eventOb.gestureScroll,
                                              userTriggered: eventOb.userTriggered,
                                              attachmentSize: eventOb.attachmentSize,
                                              timestampInMillis: eventOb.timestampInMillis,
                                              batchId: eventOb.batchId,
                                              lifecycleApp: eventOb.lifecycleApp,
                                              lifecycleViewController: eventOb.lifecycleViewController,
                                              lifecycleSwiftUI: eventOb.lifecycleSwiftUI,
                                              cpuUsage: eventOb.cpuUsage,
                                              memoryUsage: eventOb.memoryUsage,
                                              coldLaunch: eventOb.coldLaunch,
                                              warmLaunch: eventOb.warmLaunch,
                                              hotLaunch: eventOb.hotLaunch,
                                              http: eventOb.http))
                }
            } catch {
                guard let self = self else {
                    return
                }
                self.logger.internalLog(level: .error, message: "Failed to fetch sessions.", error: error, data: nil)
            }
        }
        return events.isEmpty ? nil : events
    }

    func getUnBatchedEventsWithAttachmentSize(eventCount: Number, ascending: Bool, sessionId: String?) -> [String: Number] {
        let context = coreDataManager.backgroundContext
        let fetchRequest: NSFetchRequest<EventOb> = EventOb.fetchRequest()

        fetchRequest.fetchLimit = Int(eventCount)

        let sortDescriptor = NSSortDescriptor(key: "timestampInMillis", ascending: ascending)
        fetchRequest.sortDescriptors = [sortDescriptor]

        var predicates = [NSPredicate]()

        predicates.append(NSPredicate(format: "batchId == nil"))
        if let sessionId = sessionId {
            predicates.append(NSPredicate(format: "sessionId == %@", sessionId))
        }

        if !predicates.isEmpty {
            fetchRequest.predicate = NSCompoundPredicate(andPredicateWithSubpredicates: predicates)
        }

        var eventIdAttachmentSizeMap: [String: Int64] = [:]

        context.performAndWait { [weak self] in
            do {
                let events = try context.fetch(fetchRequest)
                for event in events {
                    if let eventId = event.id {
                        eventIdAttachmentSizeMap[eventId] = event.attachmentSize
                    }
                }
            } catch {
                guard let self = self else { return }
                self.logger.internalLog(level: .error, message: "Failed to fetch events: \(error)", error: error, data: nil)
            }
        }

        return eventIdAttachmentSizeMap
    }

    func updateBatchId(_ batchId: String, for events: [String]) {
        let context = coreDataManager.backgroundContext
        let fetchRequest: NSFetchRequest<EventOb> = EventOb.fetchRequest()
        fetchRequest.predicate = NSPredicate(format: "id IN %@", events)

        context.performAndWait { [weak self] in
            do {
                let fetchedEvents = try context.fetch(fetchRequest)

                for event in fetchedEvents {
                    event.batchId = batchId
                }

                try context.saveIfNeeded()
            } catch {
                guard let self = self else { return }
                self.logger.internalLog(level: .error, message: "Failed to update batchId for events.", error: error, data: nil)
            }
        }
    }
}
