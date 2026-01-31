# Offline Mode Feature

## Overview

The WakaTime plugin now supports an offline mode that allows you to save coding activity data locally in an SQLite database instead of sending it immediately to the WakaTime API. This is useful for:

- Working in environments with limited or no internet connectivity
- Privacy-conscious users who want to control when their data is uploaded
- Batch uploading data at convenient times

## Features

### 1. Local SQLite Storage
- All heartbeat data is stored in a local SQLite database
- Database location: `~/Developer/wakatime_heartbeats.db` (on all platforms: Windows, macOS, Linux)
- The database automatically creates the necessary schema on first use

### 2. Offline Mode Toggle
- Enable/disable offline mode from the WakaTime Settings dialog
- Path: `Tools → WakaTime Settings → Offline Mode (save locally)`
- When enabled, all coding activity is saved locally instead of being sent to the API

### 3. Manual Push
- Push stored heartbeats to WakaTime at any time
- Available in Settings: `Tools → WakaTime Settings → Push Now button`
- Shows confirmation dialog with count of stored heartbeats before pushing
- Data is sent in batches of 100 to avoid overwhelming the API
- Local database is cleared after successful push

### 4. 7-Day Reminder Notification
- If you haven't pushed data in 7 days, a notification appears on IDE startup
- Notification shows the count of stored heartbeats
- One-click "Push Now" button to upload data immediately
- "Later" option to dismiss the notification

## How to Use

### Enabling Offline Mode

1. Open IDE Settings: `Tools → WakaTime Settings`
2. Check the "Offline Mode (save locally)" checkbox
3. Click "Save"
4. From this point, all coding activity will be saved locally

### Pushing Data Manually

1. Open IDE Settings: `Tools → WakaTime Settings`
2. Click the "Push Now" button
3. Confirm the push operation
4. Wait for the success notification

### Disabling Offline Mode

1. Open IDE Settings: `Tools → WakaTime Settings`
2. Uncheck the "Offline Mode (save locally)" checkbox
3. Click "Save"
4. From this point, coding activity will be sent directly to the API

**Note:** When disabling offline mode, any stored heartbeats in the local database will remain there. Make sure to push them first if you want them uploaded.

## Configuration

The offline mode settings are stored in `~/.wakatime.cfg`:

```ini
[settings]
offline_mode = true
last_push_timestamp = 1706745600000
```

- `offline_mode`: Boolean flag to enable/disable offline mode
- `last_push_timestamp`: Unix timestamp (in milliseconds) of the last successful push

## Database Schema

The local SQLite database uses the following schema:

```sql
CREATE TABLE heartbeats (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entity TEXT NOT NULL,
    timestamp REAL NOT NULL,
    is_write INTEGER NOT NULL,
    is_unsaved_file INTEGER NOT NULL,
    is_building INTEGER NOT NULL,
    project TEXT,
    language TEXT,
    line_count INTEGER,
    line_number INTEGER,
    cursor_position INTEGER,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_timestamp ON heartbeats(timestamp);
```

## Technical Details

### Database Location
The database is stored in the user's `Developer` folder:
- **Windows**: `C:\Users\<username>\Developer\wakatime_heartbeats.db`
- **macOS**: `/Users/<username>/Developer/wakatime_heartbeats.db`
- **Linux**: `/home/<username>/Developer/wakatime_heartbeats.db`

The folder is created automatically if it doesn't exist.

### Data Integrity
- Database operations are synchronized to prevent concurrent access issues
- Failed pushes do not clear the local database
- The local database persists across IDE restarts

### Performance
- Heartbeats are queued in memory first (existing behavior)
- Database writes happen in a background thread
- Batch operations are used when pushing large amounts of data

## Dependencies

- SQLite JDBC Driver (sqlite-jdbc-3.45.0.0.jar) - bundled with the plugin

## Troubleshooting

### Database Not Created
- Check that you have write permissions in your `~/Developer` folder
- Check the IDE logs (`Help → Show Log`) for any database-related errors

### Push Fails
- Ensure you have a valid API key configured
- Check your internet connection
- Check the IDE logs for detailed error messages

### Database Corruption
If the database becomes corrupted:
1. Close your IDE
2. Delete the database file: `~/Developer/wakatime_heartbeats.db`
3. Restart your IDE
4. The database will be recreated automatically

## Privacy Considerations

When using offline mode:
- Your coding activity is stored only on your local machine
- No data is sent to WakaTime servers until you explicitly push
- You have full control over when and what data is uploaded
- The local database file is not encrypted (standard SQLite file)
