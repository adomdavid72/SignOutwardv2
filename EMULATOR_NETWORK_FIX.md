# Android Emulator Internet Connectivity Fix

## Quick Fixes (Try in Order)

### 1. **Restart Emulator**
   - Close the emulator completely (click X or use Stop button)
   - Wait 5-10 seconds
   - Start the emulator again from Android Studio
   - Wait for it to fully boot (home screen appears)

### 2. **Wipe Emulator Data** (Alternative to Cold Boot)
   - In Android Studio: **Tools → Device Manager**
   - Click the **▼** dropdown next to your emulator
   - Select **Wipe Data**
   - Confirm the wipe
   - Start the emulator again (this will take longer as it rebuilds)

### 3. **Check Emulator Network Settings**
   - In Android Studio: **Tools → Device Manager**
   - Click **Edit** (pencil icon) next to your emulator
   - Go to **Advanced Settings** (or **Show Advanced Settings**)
   - Ensure **Network** is set to **Automatic** or **NAT**
   - Click **Finish** and restart emulator

### 4. **Fix DNS in Emulator**
   - Open emulator
   - Go to **Settings → Network & Internet → Wi-Fi**
   - Long-press on "AndroidWifi" → **Modify network** (or tap the network)
   - Tap **Advanced options**
   - Set DNS 1: `8.8.8.8` (Google DNS)
   - Set DNS 2: `8.8.4.4`
   - Save and reconnect

### 5. **Test Internet Connection in Emulator**
   - Open **Browser** app in emulator
   - Try visiting: `https://www.google.com`
   - If this fails, the issue is with emulator network, not your app

### 6. **Restart ADB** (In Android Studio Terminal)
   ```bash
   adb kill-server
   adb start-server
   ```
   Then restart the emulator.

### 7. **Check Firewall/Antivirus**
   - Temporarily disable firewall/antivirus
   - Check if emulator can connect
   - If it works, add exception for Android Studio/Emulator

### 8. **Use Different Network Mode**
   - In AVD Manager → Edit → Advanced Settings
   - Try changing **Network** from **NAT** to **Bridge** (requires admin on some systems)
   - Or try **TUN/TAP** if available

### 9. **Create New Emulator**
   - In AVD Manager, click **Create Device**
   - Choose a device (e.g., Pixel 6)
   - Select a system image with **Google APIs** (not Google Play)
   - In **Advanced Settings**, ensure Network is set to **Automatic**
   - Finish and start the new emulator

### 10. **Power Off and Restart Emulator**
   - In emulator, hold the **Power** button (or use menu)
   - Select **Power off**
   - Wait for it to fully shut down
   - Start it again from Android Studio

## Verify Network in App

After fixing, test your app:
1. Open the app in emulator
2. Try pairing with code "43CFBE"
3. Check Logcat for network errors:
   ```
   Filter: SupabaseClient
   Look for: Connection errors, timeout errors, DNS errors
   ```

## Common Error Messages

- **"Unable to resolve host"** → DNS issue (fix #4)
- **"Connection refused"** → Firewall/proxy issue (fix #7)
- **"Network is unreachable"** → Emulator network config (fix #3)
- **"Timeout"** → Network too slow or blocked (fix #1, #7)
- **"No internet connection"** → Emulator network not initialized (fix #1, #2)

## Alternative: Use Physical Device (Recommended)

If emulator continues to have issues:
1. Enable **Developer Options** on physical device:
   - Go to **Settings → About Phone**
   - Tap **Build Number** 7 times
2. Enable **USB Debugging**:
   - Go to **Settings → Developer Options**
   - Enable **USB Debugging**
3. Connect device via USB
4. In Android Studio, select your device instead of emulator
5. Run app on physical device

Physical devices typically have better network connectivity than emulators.

## Quick Test Commands (In Android Studio Terminal)

```bash
# Check if emulator is running
adb devices

# Test DNS resolution
adb shell ping -c 3 8.8.8.8

# Test internet connectivity
adb shell ping -c 3 google.com

# Check network configuration
adb shell getprop | grep net
```

If ping to 8.8.8.8 works but ping to google.com fails, it's a DNS issue (use fix #4).
