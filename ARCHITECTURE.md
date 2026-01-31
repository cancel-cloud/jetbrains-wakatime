# Offline Mode Feature Flow Diagram

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         User Actions                              │
└─────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                      IDE Event Listeners                          │
│  (Document Edit, File Save, Mouse Click, Caret Move, etc.)      │
└─────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                    WakaTime.appendHeartbeat()                    │
│              Creates Heartbeat with metadata                      │
└─────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Heartbeat Queue (In-Memory)                    │
│              ConcurrentLinkedQueue<Heartbeat>                    │
└─────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│            Queue Processor (Runs every 30 seconds)               │
│              WakaTime.processHeartbeatQueue()                    │
└─────────────────────────────────────────────────────────────────┘
                                 │
                    ┌────────────┴────────────┐
                    │                         │
         Offline Mode ON?          Offline Mode OFF?
                    │                         │
                    ▼                         ▼
    ┌───────────────────────────┐  ┌────────────────────────┐
    │  LocalDatabase.insert()   │  │  sendHeartbeat() to    │
    │  Save to SQLite           │  │  wakatime-cli          │
    │  ~/Developer/             │  │  (immediate upload)    │
    │  wakatime_heartbeats.db   │  │                        │
    └───────────────────────────┘  └────────────────────────┘
                    │                         │
                    │                         ▼
                    │              ┌────────────────────────┐
                    │              │  WakaTime API          │
                    │              │  (wakatime.com)        │
                    │              └────────────────────────┘
                    │
                    ▼
    ┌───────────────────────────────────────────────────────┐
    │                 SQLite Database                        │
    │  Table: heartbeats                                     │
    │  • entity (file path)                                  │
    │  • timestamp                                           │
    │  • is_write, is_unsaved_file, is_building             │
    │  • project, language                                   │
    │  • line_count, line_number, cursor_position           │
    │  • created_at                                          │
    └───────────────────────────────────────────────────────┘
                    │
                    │ (Data persists locally)
                    │
                    ▼
    ┌───────────────────────────────────────────────────────┐
    │              Manual Push Trigger                       │
    │  1. User clicks "Push Now" in Settings                │
    │  2. 7-day reminder notification                        │
    └───────────────────────────────────────────────────────┘
                    │
                    ▼
    ┌───────────────────────────────────────────────────────┐
    │          WakaTime.pushStoredHeartbeats()              │
    │  • Read all heartbeats from database                  │
    │  • Send in batches of 100 to wakatime-cli            │
    │  • Wait for completion                                │
    └───────────────────────────────────────────────────────┘
                    │
                    ▼
    ┌───────────────────────────────────────────────────────┐
    │              wakatime-cli (batch upload)              │
    │  Handles authentication, retry, offline queue         │
    └───────────────────────────────────────────────────────┘
                    │
                    ▼
    ┌───────────────────────────────────────────────────────┐
    │                  WakaTime API                         │
    │              (wakatime.com/api)                       │
    └───────────────────────────────────────────────────────┘
                    │
                    ▼
    ┌───────────────────────────────────────────────────────┐
    │         LocalDatabase.clearAllHeartbeats()            │
    │  • Delete all records from database                   │
    │  • Update last_push_timestamp in config              │
    └───────────────────────────────────────────────────────┘
```

## Component Details

### 1. Event Listeners (Existing)
- CustomDocumentListener - document edits
- CustomSaveListener - file saves
- CustomEditorMouseListener - mouse clicks
- CustomVisibleAreaListener - scrolling
- CustomCaretListener - caret movement

### 2. Heartbeat Queue (Existing)
- In-memory queue: ConcurrentLinkedQueue
- Thread-safe concurrent access
- Processed every 30 seconds

### 3. LocalDatabase (NEW)
- Manages SQLite connection
- CRUD operations for heartbeats
- Thread-safe with synchronized methods
- Auto-initialization

### 4. Settings Dialog (MODIFIED)
- Added offline mode checkbox
- Added push button
- Reads/writes offline_mode config
- Shows confirmation dialogs

### 5. WakaTime Main Class (MODIFIED)
- Added OFFLINE_MODE flag
- Modified processHeartbeatQueue() for routing
- Added pushStoredHeartbeats() method
- Added check7DayNotification() method

## Configuration Flow

```
┌──────────────────────────────────────────────────────────┐
│                    ~/.wakatime.cfg                        │
│                                                           │
│  [settings]                                              │
│  api_key = xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx         │
│  offline_mode = true                                     │
│  last_push_timestamp = 1706745600000                     │
│                                                           │
└──────────────────────────────────────────────────────────┘
                         │
                         │ Read on startup
                         ▼
            ┌────────────────────────┐
            │ WakaTime.setupConfigs()│
            │ Sets OFFLINE_MODE flag │
            └────────────────────────┘
                         │
                         ▼
            ┌────────────────────────┐
            │ Initialize LocalDatabase│
            │ if offline mode enabled│
            └────────────────────────┘
```

## Notification Flow

```
┌──────────────────────────────────────────────────────────┐
│                   IDE Startup                             │
└──────────────────────────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────┐
│          WakaTime.check7DayNotification()                │
│  • Read last_push_timestamp from config                  │
│  • Calculate days since last push                        │
│  • Check if >= 7 days                                    │
└──────────────────────────────────────────────────────────┘
                         │
                         │ 7+ days?
                         ▼
            ┌──────────────────────┐
            │  Show Dialog         │
            │  "You have X stored  │
            │   heartbeats..."     │
            │                      │
            │  [Push Now] [Later]  │
            └──────────────────────┘
                    │        │
        Push Now    │        │  Later
                    │        │
                    ▼        ▼
    ┌────────────────┐  ┌───────────┐
    │ Push data      │  │ Dismiss   │
    │ Update         │  │ (remind   │
    │ timestamp      │  │  again    │
    └────────────────┘  │  on next  │
                        │  startup) │
                        └───────────┘
```

## User Journey: First Time Setup

```
1. User installs/updates plugin
   ↓
2. Opens Tools → WakaTime Settings
   ↓
3. Sees new "Offline Mode" checkbox
   ↓
4. Checks "Offline Mode (save locally)"
   ↓
5. Clicks "Save"
   ↓
6. Plugin initializes SQLite database in ~/Developer/
   ↓
7. User continues coding normally
   ↓
8. All heartbeats saved to local database
   ↓
9. After 1 week, notification appears
   ↓
10. User clicks "Push Now" in notification
    ↓
11. Data uploaded to WakaTime API
    ↓
12. Local database cleared
    ↓
13. Timestamp updated
    ↓
14. Cycle repeats
```

## Data Flow Comparison

### Before (Online Mode)
```
IDE Events → Queue → Process (30s) → wakatime-cli → API
                                      (immediate)
```

### After (Offline Mode)
```
IDE Events → Queue → Process (30s) → SQLite DB → (stored)
                                                     ↓
                                              (user triggers)
                                                     ↓
                                              Batch Upload
                                                     ↓
                                              wakatime-cli
                                                     ↓
                                                   API
```

## Database Schema

```sql
CREATE TABLE heartbeats (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entity TEXT NOT NULL,              -- File path
    timestamp REAL NOT NULL,            -- Unix timestamp
    is_write INTEGER NOT NULL,          -- 0 or 1
    is_unsaved_file INTEGER NOT NULL,   -- 0 or 1
    is_building INTEGER NOT NULL,       -- 0 or 1
    project TEXT,                       -- Project name
    language TEXT,                      -- Programming language
    line_count INTEGER,                 -- Lines in file
    line_number INTEGER,                -- Current line
    cursor_position INTEGER,            -- Cursor position
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_timestamp ON heartbeats(timestamp);
```

## Thread Safety

```
┌──────────────────────────────────────────────────────┐
│              Multiple IDE Threads                     │
│  (UI Thread, Background Tasks, Event Handlers)       │
└──────────────────────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────┐
│          ConcurrentLinkedQueue (Thread-Safe)         │
│              Collects heartbeats                      │
└──────────────────────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────┐
│      Scheduled Thread (Single, Runs every 30s)       │
│         Processes queue sequentially                  │
└──────────────────────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────┐
│   LocalDatabase (Synchronized Methods)               │
│   • synchronized insertHeartbeat()                   │
│   • synchronized getAllHeartbeats()                  │
│   • synchronized clearAllHeartbeats()                │
│   Single SQLite connection, thread-safe access       │
└──────────────────────────────────────────────────────┘
```

This ensures:
- No race conditions
- No data corruption
- No concurrent database access issues
- Safe from multiple threads
