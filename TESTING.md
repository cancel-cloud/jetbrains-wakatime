# Manual Testing Guide for Offline Mode

This document provides step-by-step instructions for manually testing the offline mode feature.

## Prerequisites

1. Have IntelliJ IDEA or another JetBrains IDE installed
2. Install the WakaTime plugin with offline mode changes
3. Have a valid WakaTime API key

## Test 1: Enable Offline Mode

**Steps:**
1. Open IDE
2. Go to `Tools → WakaTime Settings`
3. Check the "Offline Mode (save locally)" checkbox
4. Click "Save"

**Expected Result:**
- Settings dialog closes without errors
- No error messages in IDE logs (`Help → Show Log`)

**Verification:**
- Check that `~/.wakatime.cfg` contains `offline_mode = true`
- Check IDE log for "Local database initialized successfully" message

## Test 2: Verify Local Database Creation

**Steps:**
1. Enable offline mode (Test 1)
2. Edit some code files for a few minutes
3. Navigate to `~/Developer/` folder

**Expected Result:**
- File `wakatime_heartbeats.db` exists in `~/Developer/` folder
- File size is greater than 0 bytes

**Verification:**
```bash
# Check file exists and size
ls -lh ~/Developer/wakatime_heartbeats.db

# Optional: Check database content using sqlite3
sqlite3 ~/Developer/wakatime_heartbeats.db "SELECT COUNT(*) FROM heartbeats;"
```

## Test 3: Verify Heartbeats Are Stored Locally

**Steps:**
1. Enable offline mode
2. Edit code for 5 minutes
3. Open `Tools → WakaTime Settings`
4. Note the message when clicking "Push Now" (should show heartbeat count)

**Expected Result:**
- "Push Now" dialog shows non-zero count of stored heartbeats
- Count should be reasonable for 5 minutes of coding (typically 5-20 heartbeats)

## Test 4: Manual Push Functionality

**Steps:**
1. Enable offline mode
2. Edit code for a few minutes
3. Ensure you have internet connection
4. Open `Tools → WakaTime Settings`
5. Click "Push Now" button
6. Confirm the push in the dialog

**Expected Result:**
- Progress/loading indicator appears
- Success message: "Successfully pushed X heartbeats to WakaTime"
- After closing dialogs, check WakaTime dashboard - should show recent activity

**Verification:**
```bash
# Database should be empty after push
sqlite3 ~/Developer/wakatime_heartbeats.db "SELECT COUNT(*) FROM heartbeats;"
# Expected output: 0
```

## Test 5: Push Button When Offline Mode Disabled

**Steps:**
1. Disable offline mode in settings
2. Click "Save"
3. Re-open `Tools → WakaTime Settings`
4. Click "Push Now" button

**Expected Result:**
- Message appears: "Offline mode is not enabled. Enable it first to use local storage."

## Test 6: 7-Day Notification (Simulated)

**Steps:**
1. Enable offline mode
2. Edit code for a few minutes
3. Close IDE
4. Edit `~/.wakatime.cfg` and set `last_push_timestamp` to a timestamp from 8 days ago:
   ```ini
   [settings]
   last_push_timestamp = 1706054400000
   ```
5. Restart IDE

**Expected Result:**
- Dialog appears on startup with message about stored heartbeats
- Dialog shows "Push Now" and "Later" buttons
- Clicking "Push Now" initiates push operation
- Clicking "Later" closes dialog without pushing

## Test 7: Switch Between Online and Offline Modes

**Steps:**
1. Enable offline mode, edit code for 2 minutes
2. Disable offline mode, edit code for 2 minutes
3. Enable offline mode again, edit code for 2 minutes
4. Check heartbeat count

**Expected Result:**
- First 2 minutes: stored locally
- Middle 2 minutes: sent directly to API (not stored locally)
- Last 2 minutes: stored locally
- Total stored locally: ~4-8 heartbeats (not including middle 2 minutes)

## Test 8: Large Dataset Push

**Steps:**
1. Enable offline mode
2. Edit code extensively for 1-2 hours
3. Push the data using "Push Now" button

**Expected Result:**
- Push completes successfully without errors
- All data appears on WakaTime dashboard
- No timeout errors
- Local database is cleared after push

**Verification:**
- Check IDE logs for "Successfully pushed X heartbeats" message
- Visit WakaTime dashboard and verify time logged matches coding session

## Test 9: Database Location on Different Operating Systems

**Windows:**
```
C:\Users\<YourUsername>\Developer\wakatime_heartbeats.db
```

**macOS:**
```
/Users/<YourUsername>/Developer/wakatime_heartbeats.db
```

**Linux:**
```
/home/<YourUsername>/Developer/wakatime_heartbeats.db
```

**Steps:**
1. Enable offline mode on your OS
2. Edit code briefly
3. Navigate to the expected database path

**Expected Result:**
- Database file exists at the correct platform-specific path

## Test 10: Database Persistence Across IDE Restarts

**Steps:**
1. Enable offline mode
2. Edit code for 5 minutes
3. Note the heartbeat count (via "Push Now" dialog)
4. Close IDE completely
5. Restart IDE
6. Open `Tools → WakaTime Settings`
7. Check heartbeat count again

**Expected Result:**
- Heartbeat count after restart matches count before closing
- No data loss occurred

## Error Scenarios

### Test E1: No API Key Configured

**Steps:**
1. Remove API key from `~/.wakatime.cfg`
2. Enable offline mode
3. Edit code for a few minutes
4. Try to push data

**Expected Result:**
- Either: API key prompt appears before push
- Or: Push fails with appropriate error message

### Test E2: No Internet Connection

**Steps:**
1. Enable offline mode
2. Edit code for a few minutes
3. Disconnect from internet
4. Try to push data

**Expected Result:**
- Push fails with connection error
- Data remains in local database (not cleared)
- Can retry push after reconnecting

### Test E3: Invalid API Key

**Steps:**
1. Set invalid API key in `~/.wakatime.cfg`
2. Enable offline mode and collect some data
3. Try to push data

**Expected Result:**
- Push fails with authentication error
- Data remains in local database
- User can fix API key and retry

## Cleanup

After testing, you may want to clean up:

```bash
# Remove test database
rm ~/Developer/wakatime_heartbeats.db

# Reset wakatime config
# Edit ~/.wakatime.cfg and remove offline_mode and last_push_timestamp settings
```

## Common Issues

### Database Won't Create
- Check permissions on `~/Developer` folder
- Check IDE logs for errors
- Try manually creating the folder: `mkdir -p ~/Developer`

### Push Does Nothing
- Check IDE logs (`Help → Show Log`)
- Verify API key is valid
- Verify internet connection
- Check that offline mode is enabled

### Notification Doesn't Appear
- Verify offline mode is enabled
- Verify `last_push_timestamp` is set correctly
- Verify there are heartbeats in database
- Try completely restarting IDE
