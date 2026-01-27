# Fullscreen & Camera Testing Guide

## ✅ What's Fixed

### 1. **Camera Stream** 📹
- Camera permission flow now works correctly
- Video stream attaches properly to video element using React useEffect
- Detailed console logging for debugging

### 2. **Fullscreen Mode** 🖥️
- Fullscreen now triggers from user button click (browser requirement)
- Proper error handling if fullscreen fails
- Warning banner if user exits fullscreen during exam

---

## 🧪 Testing Steps

### **Test 1: Camera & Identity Verification**

1. **Start Test Preview**
   - Go to Admin Dashboard → Exams
   - Click **"Preview"** button on a proctored exam
   
2. **Allow Camera Permission**
   - Proctoring Setup Dialog appears
   - Click **"Allow Camera Access"**
   - Browser will ask for camera permission → Click **"Allow"**
   
3. **Check Console Logs**
   ```
   Requesting camera permission...
   Camera permission granted, stream obtained: MediaStream {...}
   Video tracks: [VideoStreamTrack]
   Active tracks: true, "live"
   Attaching stream to video element in useEffect
   Waiting for video metadata...
   Video metadata loaded
   Attempting to play video...
   Video playing successfully!
   ```

4. **Capture Photo**
   - ✅ You should see **your live camera feed** (not black screen)
   - Position yourself in center
   - Click **"Capture Photo"**

5. **Setup Complete Screen**
   - ✅ Shows "Setup Complete" with green checkmark
   - Shows "Start Exam" button
   - Shows message: "The exam will open in fullscreen mode"

---

### **Test 2: Fullscreen Activation**

1. **Start Exam**
   - After capturing photo, click **"Start Exam"** button
   - ✅ **Page should immediately enter fullscreen mode**
   - Exam questions should be visible in fullscreen

2. **Check Console**
   ```
   [No errors should appear]
   ```

3. **Exit Fullscreen (Test Warning)**
   - Press **ESC** key to exit fullscreen
   - ✅ **Yellow warning banner should appear** at top of exam
   - Banner says: "Fullscreen Mode Required"
   - Shows "Enter Fullscreen" button

4. **Re-enter Fullscreen**
   - Click "Enter Fullscreen" button in warning banner
   - ✅ Should re-enter fullscreen mode
   - Warning banner disappears

---

### **Test 3: Tab Switching Detection**

1. **Switch Tabs While In Exam**
   - Press **Alt+Tab** (Windows/Linux) or **Cmd+Tab** (Mac)
   - Switch to another application/tab
   - Switch back to exam

2. **Check Console** (in preview mode)
   ```
   [PREVIEW] Proctoring Event: {
     eventType: "TAB_SWITCH",
     severity: "WARNING",
     metadata: { count: 1, maxAllowed: 3 }
   }
   ```

3. **Exceed Tab Switch Limit**
   - Switch tabs **3 times** (default limit)
   - ✅ **Alert should appear**: 
     > "Warning: You have reached the maximum allowed tab switches (3)"

---

## 🔍 Troubleshooting

### **Camera Not Showing (Black Screen)**

**Check Browser Console for:**
- ❌ `Camera permission denied` → Grant permission in browser settings
- ❌ `No video tracks` → Camera in use by another app (close Zoom, Teams, etc.)
- ❌ `Video play error` → Browser blocking autoplay (shouldn't happen with our fix)

**Solutions:**
1. Close other apps using camera
2. Check browser permissions: `Settings → Privacy → Camera`
3. Hard refresh page: `Ctrl+Shift+R` (Windows) or `Cmd+Shift+R` (Mac)

---

### **Fullscreen Not Working**

**Possible Issues:**
- ❌ Browser doesn't support fullscreen API (very old browsers)
- ❌ Browser extension blocking fullscreen
- ❌ Security settings blocking fullscreen

**Check Console for:**
```
Fullscreen request failed: [error message]
```

**Solutions:**
1. Disable browser extensions (ad blockers, etc.)
2. Try different browser (Chrome, Firefox, Edge)
3. Check browser allows fullscreen for localhost

---

### **Firefox-Specific Notes**

Firefox may require additional user interaction. The "Start Exam" button click **is** a user interaction, so it should work. If it doesn't:

1. Check Firefox settings: `about:config`
2. Search: `full-screen-api.allow-trusted-requests-only`
3. Ensure it's set to `false` or browser trusts localhost

---

## 📋 Expected User Flow

```
1. Click "Preview" in Admin Dashboard
   ↓
2. Proctoring Setup Dialog opens
   ↓
3. Click "Allow Camera Access" button
   ↓
4. Browser asks for camera permission → Click "Allow"
   ↓
5. Camera feed appears (live video)
   ↓
6. Click "Capture Photo" button
   ↓
7. "Setup Complete" screen shows
   ↓
8. Click "Start Exam" button
   ↓
9. ✅ Page enters FULLSCREEN mode
   ↓
10. Exam starts with dummy questions
   ↓
11. Tab switches are logged (check console)
   ↓
12. Press ESC → Warning banner appears
   ↓
13. Click "Enter Fullscreen" → Fullscreen restored
   ↓
14. Click "Close Preview" → Exit exam
```

---

## 🎯 What to Report Back

**Please test and report:**

1. ✅ Is camera feed visible? (not black)
2. ✅ Does fullscreen activate when clicking "Start Exam"?
3. ✅ Does warning banner appear when exiting fullscreen?
4. ✅ Do tab switches log to console in preview mode?
5. ❌ Any errors in browser console?

**Browser & OS:**
- Browser: (Chrome/Firefox/Safari/Edge)
- Version: 
- OS: (Mac/Windows/Linux)

---

## 🚀 Ready to Test!

1. **Restart dev server** (if running):
   ```bash
   # Kill current server
   # Restart
   npm run dev
   ```

2. **Hard refresh browser**: `Ctrl+Shift+R` or `Cmd+Shift+R`

3. **Open Admin Dashboard** → Navigate to Exams

4. **Click "Preview"** on a proctored exam

5. **Follow the user flow** above ☝️

---

## 📝 Key Changes Made

### Camera Fix:
- Moved stream attachment to `useEffect` with proper dependencies
- Added async/await for video metadata loading
- Simplified permission request flow
- Enhanced console logging

### Fullscreen Fix:
- Added "Start Exam" button in completion screen
- Button directly triggers fullscreen (user gesture)
- Added warning banner for fullscreen exit
- Added re-enter button in warning

### Tab Switch Fix:
- Separated visibility change detection into own useEffect
- Fixed dependency issues
- Proper event logging
- Alert when limit reached
