## 1.0.11

* **BREAKING CHANGE**: Updated ping calculation formula - now divides by 4 instead of 3 for more accurate real ping values
* **Major Optimization**: Completely optimized V2rayCoreManager for crash prevention:
  - Enhanced `startCore()` with comprehensive input validation and native error handling
  - Improved `makeDurationTimer()` with safe statistics collection and broadcast handling
  - Optimized `setUpListener()` with proper service validation and native library error handling
  - Enhanced `stopCore()` with safe notification cancellation and emergency state cleanup
  - Added defensive programming to all native callback methods (shutdown, protect, setup)
* **Stability Improvements**: 
  - Added UnsatisfiedLinkError handling for all native library calls
  - Implemented comprehensive null pointer exception prevention
  - Enhanced error logging and graceful failure handling
  - Added proper state management and cleanup mechanisms
* **Ping Methods Enhancement**: 
  - Improved error handling in `getConnectedV2rayServerDelay()` and `getV2rayServerDelay()`
  - Added result validation with maximum 30-second timeout
  - Enhanced JSON processing with robust fallback mechanisms
* **Build Fixes**:
  - Fixed Java compilation error with JSONObject.keys() iterator
  - Updated NDK version to 27.0.12077973 for compatibility
  - Added missing Iterator import

## 1.0.10

* update xray core version to 25.3.6

## 1.0.9

* add disconnect button (notificationDisconnectButtonName)
* fix requestPermission
* fix notification bugs  (android 13 and higher)

## 1.0.8

* fix registerReceiver error
* fix build for API 34 ( check IntentFilter V2RAY_CONNECTION_INFO )
* change type of usage statistic
* fix deprecated apis
* update v2ray core to 1.8.17

## 1.0.7

* fix v2rayBroadCastReceiver null exception
* fix #35: registerReceiver error
* update libv2ray to 1.8.7
* fix #43: Adding DNS servers to the VPN service

## 1.0.6

* fix #24 issue: fix notification and background service
* fix #11 issue: add bypassSubnets for bypass lan traffic
* add getConnectedServerDelay
* add getCoreVersion
* optimize java codes

## 1.0.5

* fix #10 issue: notification service

## 1.0.4

* fix getServerDelay

## 1.0.3

* fix vless fingerprint 
* update android XRay version

## 1.0.2

* fix #4 issue: tlsSettings -> path
* fix #7 issue: reset total traffic after disconnect 
* validate json config

## 1.0.1

* fix EventChannel crash
* add getServerDelay method

## 1.0.0

* implement v2ray for android
* create v2ray share link parser
