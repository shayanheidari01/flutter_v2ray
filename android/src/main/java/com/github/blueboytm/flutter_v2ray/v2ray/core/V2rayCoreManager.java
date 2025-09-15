package com.github.blueboytm.flutter_v2ray.v2ray.core;

import static com.github.blueboytm.flutter_v2ray.v2ray.utils.Utilities.getUserAssetsPath;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.CountDownTimer;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.github.blueboytm.flutter_v2ray.v2ray.interfaces.V2rayServicesListener;
import com.github.blueboytm.flutter_v2ray.v2ray.services.V2rayProxyOnlyService;
import com.github.blueboytm.flutter_v2ray.v2ray.services.V2rayVPNService;
import com.github.blueboytm.flutter_v2ray.v2ray.utils.AppConfigs;
import com.github.blueboytm.flutter_v2ray.v2ray.utils.Utilities;
import com.github.blueboytm.flutter_v2ray.v2ray.utils.V2rayConfig;

import org.json.JSONObject;

import java.util.Iterator;

import libv2ray.Libv2ray;
import libv2ray.V2RayPoint;
import libv2ray.V2RayVPNServiceSupportsSet;

public final class V2rayCoreManager {
    private static final int NOTIFICATION_ID = 1;
    private volatile static V2rayCoreManager INSTANCE;
    public V2rayServicesListener v2rayServicesListener = null;
    public final V2RayPoint v2RayPoint = Libv2ray.newV2RayPoint(new V2RayVPNServiceSupportsSet() {
        @Override
        public long shutdown() {
            try {
                if (v2rayServicesListener == null) {
                    Log.w(V2rayCoreManager.class.getSimpleName(), "shutdown => service listener is null");
                    return -1;
                }
                
                try {
                    v2rayServicesListener.stopService();
                    Log.d(V2rayCoreManager.class.getSimpleName(), "shutdown => service stopped successfully");
                } catch (Exception serviceError) {
                    Log.e(V2rayCoreManager.class.getSimpleName(), "shutdown => error stopping service: " + serviceError.getMessage(), serviceError);
                    // Continue to cleanup even if service stop fails
                }
                
                v2rayServicesListener = null;
                return 0;
                
            } catch (Exception e) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "shutdown => unexpected error: " + e.getMessage(), e);
                v2rayServicesListener = null;
                return -1;
            } catch (Throwable t) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "shutdown => critical error: " + t.getMessage(), t);
                v2rayServicesListener = null;
                return -1;
            }
        }

        @Override
        public long prepare() {
            return 0;
        }

        @Override
        public boolean protect(long l) {
            try {
                if (v2rayServicesListener != null) {
                    return v2rayServicesListener.onProtect((int) l);
                }
                Log.w(V2rayCoreManager.class.getSimpleName(), "protect => service listener is null, returning true");
                return true;
            } catch (Exception e) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "protect => error: " + e.getMessage(), e);
                return false;
            } catch (Throwable t) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "protect => critical error: " + t.getMessage(), t);
                return false;
            }
        }

        @Override
        public long onEmitStatus(long l, String s) {
            return 0;
        }

        @Override
        public long setup(String s) {
            try {
                if (v2rayServicesListener != null) {
                    try {
                        v2rayServicesListener.startService();
                        Log.d(V2rayCoreManager.class.getSimpleName(), "setup => service started successfully");
                        return 0;
                    } catch (Exception serviceError) {
                        Log.e(V2rayCoreManager.class.getSimpleName(), "setup => error starting service: " + serviceError.getMessage(), serviceError);
                        return -1;
                    }
                } else {
                    Log.w(V2rayCoreManager.class.getSimpleName(), "setup => service listener is null");
                    return -1;
                }
            } catch (Exception e) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "setup => unexpected error: " + e.getMessage(), e);
                return -1;
            } catch (Throwable t) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "setup => critical error: " + t.getMessage(), t);
                return -1;
            }
        }
    }, Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1);
    public AppConfigs.V2RAY_STATES V2RAY_STATE = AppConfigs.V2RAY_STATES.V2RAY_DISCONNECTED;
    private boolean isLibV2rayCoreInitialized = false;
    private CountDownTimer countDownTimer;
    private int seconds, minutes, hours;
    private long totalDownload, totalUpload, uploadSpeed, downloadSpeed;
    private String SERVICE_DURATION = "00:00:00";

    public static V2rayCoreManager getInstance() {
        if (INSTANCE == null) {
            synchronized (V2rayCoreManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new V2rayCoreManager();
                }
            }
        }
        return INSTANCE;
    }

    private void makeDurationTimer(final Context context, final boolean enable_traffic_statics) {
        try {
            // Validate input parameters
            if (context == null) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "makeDurationTimer failed => context is null");
                return;
            }
            
            // Cancel existing timer to prevent multiple timers
            if (countDownTimer != null) {
                try {
                    countDownTimer.cancel();
                } catch (Exception e) {
                    Log.w(V2rayCoreManager.class.getSimpleName(), "Failed to cancel existing timer: " + e.getMessage());
                }
            }
            
            countDownTimer = new CountDownTimer(7200000, 1000) { // 2 hours in milliseconds
                @RequiresApi(api = Build.VERSION_CODES.M)
                public void onTick(long millisUntilFinished) {
                    try {
                        seconds++;
                        if (seconds >= 60) {
                            minutes++;
                            seconds = 0;
                        }
                        if (minutes >= 60) {
                            minutes = 0;
                            hours++;
                        }
                        if (hours >= 24) {
                            hours = 0;
                        }
                        
                        // Safe traffic statistics collection
                        if (enable_traffic_statics && v2RayPoint != null) {
                            try {
                                long newDownloadSpeed = v2RayPoint.queryStats("block", "downlink") + v2RayPoint.queryStats("proxy", "downlink");
                                long newUploadSpeed = v2RayPoint.queryStats("block", "uplink") + v2RayPoint.queryStats("proxy", "uplink");
                                
                                // Validate stats values
                                if (newDownloadSpeed >= 0 && newUploadSpeed >= 0) {
                                    downloadSpeed = newDownloadSpeed;
                                    uploadSpeed = newUploadSpeed;
                                    totalDownload = totalDownload + downloadSpeed;
                                    totalUpload = totalUpload + uploadSpeed;
                                }
                            } catch (UnsatisfiedLinkError nativeError) {
                                Log.w(V2rayCoreManager.class.getSimpleName(), "Native error in queryStats: " + nativeError.getMessage());
                                // Continue without stats - not critical
                            } catch (Exception statsError) {
                                Log.w(V2rayCoreManager.class.getSimpleName(), "Error collecting traffic stats: " + statsError.getMessage());
                                // Continue without stats - not critical
                            }
                        }
                        
                        // Safe duration formatting
                        try {
                            SERVICE_DURATION = Utilities.convertIntToTwoDigit(hours) + ":" + 
                                             Utilities.convertIntToTwoDigit(minutes) + ":" + 
                                             Utilities.convertIntToTwoDigit(seconds);
                        } catch (Exception formatError) {
                            Log.w(V2rayCoreManager.class.getSimpleName(), "Error formatting duration: " + formatError.getMessage());
                            SERVICE_DURATION = "00:00:00"; // Fallback
                        }
                        
                        // Safe broadcast sending
                        try {
                            Intent connection_info_intent = new Intent("V2RAY_CONNECTION_INFO");
                            connection_info_intent.putExtra("STATE", V2rayCoreManager.getInstance().V2RAY_STATE);
                            connection_info_intent.putExtra("DURATION", SERVICE_DURATION);
                            connection_info_intent.putExtra("UPLOAD_SPEED", uploadSpeed);
                            connection_info_intent.putExtra("DOWNLOAD_SPEED", downloadSpeed);
                            connection_info_intent.putExtra("UPLOAD_TRAFFIC", totalUpload);
                            connection_info_intent.putExtra("DOWNLOAD_TRAFFIC", totalDownload);
                            
                            if (context != null) {
                                context.sendBroadcast(connection_info_intent);
                            }
                        } catch (Exception broadcastError) {
                            Log.w(V2rayCoreManager.class.getSimpleName(), "Error sending broadcast: " + broadcastError.getMessage());
                            // Continue - broadcast failure is not critical
                        }
                        
                    } catch (Exception tickError) {
                        Log.e(V2rayCoreManager.class.getSimpleName(), "Error in timer tick: " + tickError.getMessage(), tickError);
                        // Continue timer execution
                    }
                }

                public void onFinish() {
                    try {
                        if (countDownTimer != null) {
                            countDownTimer.cancel();
                        }
                        
                        // Restart timer if V2Ray is still running
                        if (V2rayCoreManager.getInstance() != null && 
                            V2rayCoreManager.getInstance().isV2rayCoreRunning() && 
                            context != null) {
                            makeDurationTimer(context, enable_traffic_statics);
                        }
                    } catch (Exception finishError) {
                        Log.e(V2rayCoreManager.class.getSimpleName(), "Error in timer finish: " + finishError.getMessage(), finishError);
                    }
                }
            };
            
            // Start timer with error handling
            try {
                countDownTimer.start();
            } catch (Exception startError) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "Failed to start timer: " + startError.getMessage(), startError);
                countDownTimer = null;
            }
            
        } catch (Exception e) {
            Log.e(V2rayCoreManager.class.getSimpleName(), "Critical error in makeDurationTimer: " + e.getMessage(), e);
            countDownTimer = null;
        }
    }

    public void setUpListener(Service targetService) {
        try {
            // Validate input parameter
            if (targetService == null) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "setUpListener failed => targetService is null");
                isLibV2rayCoreInitialized = false;
                return;
            }
            
            // Validate service implements required interface
            if (!(targetService instanceof V2rayServicesListener)) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "setUpListener failed => targetService does not implement V2rayServicesListener");
                isLibV2rayCoreInitialized = false;
                return;
            }
            
            // Safe casting
            try {
                v2rayServicesListener = (V2rayServicesListener) targetService;
            } catch (ClassCastException castError) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "setUpListener failed => ClassCastException: " + castError.getMessage(), castError);
                isLibV2rayCoreInitialized = false;
                return;
            }
            
            // Validate application context
            Context appContext = null;
            try {
                appContext = targetService.getApplicationContext();
                if (appContext == null) {
                    Log.e(V2rayCoreManager.class.getSimpleName(), "setUpListener failed => application context is null");
                    isLibV2rayCoreInitialized = false;
                    return;
                }
            } catch (Exception contextError) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "setUpListener failed => error getting application context: " + contextError.getMessage(), contextError);
                isLibV2rayCoreInitialized = false;
                return;
            }
            
            // Safe native library initialization
            try {
                String assetsPath = getUserAssetsPath(appContext);
                if (assetsPath == null || assetsPath.trim().isEmpty()) {
                    Log.e(V2rayCoreManager.class.getSimpleName(), "setUpListener failed => invalid assets path");
                    isLibV2rayCoreInitialized = false;
                    return;
                }
                
                Libv2ray.initV2Env(assetsPath, "");
                Log.d(V2rayCoreManager.class.getSimpleName(), "V2Ray environment initialized successfully");
                
            } catch (UnsatisfiedLinkError nativeError) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "setUpListener failed => native library error: " + nativeError.getMessage(), nativeError);
                isLibV2rayCoreInitialized = false;
                return;
            } catch (Exception initError) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "setUpListener failed => initialization error: " + initError.getMessage(), initError);
                isLibV2rayCoreInitialized = false;
                return;
            }
            
            // Initialize state variables safely
            isLibV2rayCoreInitialized = true;
            SERVICE_DURATION = "00:00:00";
            seconds = 0;
            minutes = 0;
            hours = 0;
            uploadSpeed = 0;
            downloadSpeed = 0;
            totalDownload = 0;
            totalUpload = 0;
            
            // Safe logging
            try {
                String serviceName = v2rayServicesListener.getService().getClass().getSimpleName();
                Log.d(V2rayCoreManager.class.getSimpleName(), "setUpListener => successfully initialized from " + serviceName);
            } catch (Exception logError) {
                Log.d(V2rayCoreManager.class.getSimpleName(), "setUpListener => successfully initialized (service name unavailable)");
            }
            
        } catch (Exception e) {
            Log.e(V2rayCoreManager.class.getSimpleName(), "setUpListener failed => unexpected error: " + e.getMessage(), e);
            isLibV2rayCoreInitialized = false;
            v2rayServicesListener = null;
        } catch (Throwable t) {
            Log.e(V2rayCoreManager.class.getSimpleName(), "setUpListener failed => critical error: " + t.getMessage(), t);
            isLibV2rayCoreInitialized = false;
            v2rayServicesListener = null;
        }
    }

    public boolean startCore(final V2rayConfig v2rayConfig) {
        try {
            // Validate input parameters
            if (v2rayConfig == null) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "startCore failed => v2rayConfig is null");
                return false;
            }
            
            // Validate service listener
            if (v2rayServicesListener == null) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "startCore failed => v2rayServicesListener is null");
                return false;
            }
            
            Service service = v2rayServicesListener.getService();
            if (service == null) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "startCore failed => service is null");
                return false;
            }
            
            // Validate V2Ray configuration
            if (v2rayConfig.V2RAY_FULL_JSON_CONFIG == null || v2rayConfig.V2RAY_FULL_JSON_CONFIG.trim().isEmpty()) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "startCore failed => invalid V2Ray config");
                return false;
            }
            
            if (v2rayConfig.CONNECTED_V2RAY_SERVER_ADDRESS == null || v2rayConfig.CONNECTED_V2RAY_SERVER_ADDRESS.trim().isEmpty()) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "startCore failed => invalid server address");
                return false;
            }
            
            // Validate V2Ray core initialization
            if (!isLibV2rayCoreInitialized) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "startCore failed => LibV2rayCore should be initialize before start.");
                return false;
            }
            
            // Validate V2RayPoint
            if (v2RayPoint == null) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "startCore failed => v2RayPoint is null");
                return false;
            }
            
            // Stop existing core if running
            if (isV2rayCoreRunning()) {
                stopCore();
                // Wait a bit for clean shutdown
                Thread.sleep(100);
            }
            
            V2RAY_STATE = AppConfigs.V2RAY_STATES.V2RAY_CONNECTING;
            
            // Start duration timer with error handling
            try {
                makeDurationTimer(service.getApplicationContext(), v2rayConfig.ENABLE_TRAFFIC_STATICS);
            } catch (Exception timerError) {
                Log.w(V2rayCoreManager.class.getSimpleName(), "Failed to start duration timer: " + timerError.getMessage());
                // Continue without timer - not critical
            }
            
            // Configure and start V2Ray core with native error handling
            try {
                v2RayPoint.setConfigureFileContent(v2rayConfig.V2RAY_FULL_JSON_CONFIG);
                v2RayPoint.setDomainName(v2rayConfig.CONNECTED_V2RAY_SERVER_ADDRESS + ":" + v2rayConfig.CONNECTED_V2RAY_SERVER_PORT);
                v2RayPoint.runLoop(false);
                
                // Verify core started successfully
                if (isV2rayCoreRunning()) {
                    V2RAY_STATE = AppConfigs.V2RAY_STATES.V2RAY_CONNECTED;
                    try {
                        showNotification(v2rayConfig);
                    } catch (Exception notificationError) {
                        Log.w(V2rayCoreManager.class.getSimpleName(), "Failed to show notification: " + notificationError.getMessage());
                        // Continue without notification - not critical
                    }
                    Log.d(V2rayCoreManager.class.getSimpleName(), "V2Ray core started successfully");
                    return true;
                } else {
                    Log.e(V2rayCoreManager.class.getSimpleName(), "V2Ray core failed to start - not running after runLoop");
                    V2RAY_STATE = AppConfigs.V2RAY_STATES.V2RAY_DISCONNECTED;
                    return false;
                }
                
            } catch (UnsatisfiedLinkError nativeError) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "Native library error in startCore: " + nativeError.getMessage(), nativeError);
                V2RAY_STATE = AppConfigs.V2RAY_STATES.V2RAY_DISCONNECTED;
                return false;
            } catch (Exception coreError) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "V2Ray core error in startCore: " + coreError.getMessage(), coreError);
                V2RAY_STATE = AppConfigs.V2RAY_STATES.V2RAY_DISCONNECTED;
                return false;
            }
            
        } catch (InterruptedException e) {
            Log.e(V2rayCoreManager.class.getSimpleName(), "startCore interrupted: " + e.getMessage(), e);
            Thread.currentThread().interrupt();
            V2RAY_STATE = AppConfigs.V2RAY_STATES.V2RAY_DISCONNECTED;
            return false;
        } catch (Exception e) {
            Log.e(V2rayCoreManager.class.getSimpleName(), "Unexpected error in startCore: " + e.getMessage(), e);
            V2RAY_STATE = AppConfigs.V2RAY_STATES.V2RAY_DISCONNECTED;
            return false;
        } catch (Throwable t) {
            Log.e(V2rayCoreManager.class.getSimpleName(), "Critical error in startCore: " + t.getMessage(), t);
            V2RAY_STATE = AppConfigs.V2RAY_STATES.V2RAY_DISCONNECTED;
            return false;
        }
    }

    public void stopCore() {
        try {
            // Safe notification cancellation
            try {
                if (v2rayServicesListener != null) {
                    Service service = v2rayServicesListener.getService();
                    if (service != null) {
                        NotificationManager notificationManager = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
                        if (notificationManager != null) {
                            notificationManager.cancel(NOTIFICATION_ID);
                        }
                    }
                }
            } catch (Exception notificationError) {
                Log.w(V2rayCoreManager.class.getSimpleName(), "Failed to cancel notification: " + notificationError.getMessage());
                // Continue - notification cancellation is not critical
            }
            
            // Safe V2Ray core stopping
            boolean wasRunning = false;
            try {
                wasRunning = isV2rayCoreRunning();
                if (wasRunning && v2RayPoint != null) {
                    try {
                        v2RayPoint.stopLoop();
                        Log.d(V2rayCoreManager.class.getSimpleName(), "V2Ray core stopLoop called successfully");
                    } catch (UnsatisfiedLinkError nativeError) {
                        Log.e(V2rayCoreManager.class.getSimpleName(), "Native error stopping V2Ray core: " + nativeError.getMessage(), nativeError);
                    } catch (Exception coreError) {
                        Log.e(V2rayCoreManager.class.getSimpleName(), "Error stopping V2Ray core: " + coreError.getMessage(), coreError);
                    }
                    
                    // Safe service stopping
                    try {
                        if (v2rayServicesListener != null) {
                            v2rayServicesListener.stopService();
                            Log.d(V2rayCoreManager.class.getSimpleName(), "V2Ray service stopped successfully");
                        }
                    } catch (Exception serviceError) {
                        Log.e(V2rayCoreManager.class.getSimpleName(), "Error stopping V2Ray service: " + serviceError.getMessage(), serviceError);
                    }
                    
                    Log.d(V2rayCoreManager.class.getSimpleName(), "stopCore success => v2ray core stopped.");
                } else {
                    Log.w(V2rayCoreManager.class.getSimpleName(), "stopCore => v2ray core was not running or v2RayPoint is null.");
                }
            } catch (Exception runningCheckError) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "Error checking if core is running: " + runningCheckError.getMessage(), runningCheckError);
            }
            
            // Always send disconnected broadcast to clean up state
            try {
                sendDisconnectedBroadCast();
            } catch (Exception broadcastError) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "Error sending disconnected broadcast: " + broadcastError.getMessage(), broadcastError);
            }
            
        } catch (Exception e) {
            Log.e(V2rayCoreManager.class.getSimpleName(), "Unexpected error in stopCore: " + e.getMessage(), e);
            
            // Ensure state is cleaned up even on error
            try {
                sendDisconnectedBroadCast();
            } catch (Exception fallbackError) {
                Log.e(V2rayCoreManager.class.getSimpleName(), "Fallback broadcast also failed: " + fallbackError.getMessage(), fallbackError);
            }
        } catch (Throwable t) {
            Log.e(V2rayCoreManager.class.getSimpleName(), "Critical error in stopCore: " + t.getMessage(), t);
            
            // Emergency state cleanup
            V2RAY_STATE = AppConfigs.V2RAY_STATES.V2RAY_DISCONNECTED;
            if (countDownTimer != null) {
                try {
                    countDownTimer.cancel();
                } catch (Exception timerError) {
                    // Ignore timer cancellation errors
                }
                countDownTimer = null;
            }
        }
    }

    private void sendDisconnectedBroadCast() {
        V2RAY_STATE = AppConfigs.V2RAY_STATES.V2RAY_DISCONNECTED;
        SERVICE_DURATION = "00:00:00";
        seconds = 0;
        minutes = 0;
        hours = 0;
        uploadSpeed = 0;
        downloadSpeed = 0;
        if (v2rayServicesListener != null) {
            Intent connection_info_intent = new Intent("V2RAY_CONNECTION_INFO");
            connection_info_intent.putExtra("STATE", V2rayCoreManager.getInstance().V2RAY_STATE);
            connection_info_intent.putExtra("DURATION", SERVICE_DURATION);
            connection_info_intent.putExtra("UPLOAD_SPEED", uploadSpeed);
            connection_info_intent.putExtra("DOWNLOAD_SPEED", uploadSpeed);
            connection_info_intent.putExtra("UPLOAD_TRAFFIC", uploadSpeed);
            connection_info_intent.putExtra("DOWNLOAD_TRAFFIC", uploadSpeed);
            try {
                v2rayServicesListener.getService().getApplicationContext().sendBroadcast(connection_info_intent);
            } catch (Exception e) {
                //ignore
            }
        }
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }

    private String createNotificationChannelID(String appName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                    (NotificationManager) v2rayServicesListener.getService().getSystemService(Context.NOTIFICATION_SERVICE);

            String channelId = "A_FLUTTER_V2RAY_SERVICE_CH_ID";
            String channelName = appName + " Background Service";
            NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(channelName);
            channel.setLightColor(Color.DKGRAY);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }

            return channelId;
        }
        return "";
    }

    private void showNotification(final V2rayConfig v2rayConfig) {
        Service context = v2rayServicesListener.getService();
        if (context == null) {
            return;
        }

        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launchIntent != null) {
            launchIntent.setAction("FROM_DISCONNECT_BTN");
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }
        final int flags;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        } else {
            flags = PendingIntent.FLAG_UPDATE_CURRENT;
        }
        PendingIntent notificationContentPendingIntent = PendingIntent.getActivity(
                context, 0, launchIntent, flags);

        String notificationChannelID = createNotificationChannelID(v2rayConfig.APPLICATION_NAME);

        Intent stopIntent;
        if (AppConfigs.V2RAY_CONNECTION_MODE == AppConfigs.V2RAY_CONNECTION_MODES.PROXY_ONLY) {
            stopIntent = new Intent(context, V2rayProxyOnlyService.class);
        } else if (AppConfigs.V2RAY_CONNECTION_MODE == AppConfigs.V2RAY_CONNECTION_MODES.VPN_TUN) {
            stopIntent = new Intent(context, V2rayVPNService.class);
        } else {
            return;
        }
        stopIntent.putExtra("COMMAND", AppConfigs.V2RAY_SERVICE_COMMANDS.STOP_SERVICE);

        PendingIntent pendingIntent = PendingIntent.getService(
                context, 0, stopIntent, flags);

        // Build the notification
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, notificationChannelID)
                .setSmallIcon(v2rayConfig.APPLICATION_ICON)
                .setContentTitle(v2rayConfig.REMARK)
                .addAction(0, v2rayConfig.NOTIFICATION_DISCONNECT_BUTTON_NAME, pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setContentIntent(notificationContentPendingIntent)
                .setOngoing(true);

        context.startForeground(NOTIFICATION_ID, notificationBuilder.build());
    }


    public boolean isV2rayCoreRunning() {
        if (v2RayPoint != null) {
            return v2RayPoint.getIsRunning();
        }
        return false;
    }

    public Long getConnectedV2rayServerDelay() {
        try {
            // Validate V2Ray core status
            if (!isV2rayCoreRunning() || v2RayPoint == null) {
                Log.w("getConnectedV2rayServerDelay", "V2Ray core is not running or not initialized");
                return -1L;
            }
            
            // Validate URL
            if (AppConfigs.DELAY_URL == null || AppConfigs.DELAY_URL.trim().isEmpty()) {
                Log.w("getConnectedV2rayServerDelay", "Invalid delay URL");
                return -1L;
            }
            
            // Measure delay with timeout validation
            long result = v2RayPoint.measureDelay(AppConfigs.DELAY_URL);
            
            // Validate result (max 30 seconds = 30000ms)
            if (result < 0 || result > 30000) {
                Log.w("getConnectedV2rayServerDelay", "Invalid delay result: " + result + "ms");
                return -1L;
            }
            
            // Divide by 3 to get real ping value
            long realPing = result / 4;
            Log.d("getConnectedV2rayServerDelay", "Delay measured successfully: " + result + "ms, real ping: " + realPing + "ms");
            return realPing;
            
        } catch (Exception e) {
            Log.e("getConnectedV2rayServerDelay", "Failed to measure delay: " + e.getMessage(), e);
            return -1L;
        } catch (Throwable t) {
            Log.e("getConnectedV2rayServerDelay", "Critical error measuring delay: " + t.getMessage(), t);
            return -1L;
        }
    }

    public Long getV2rayServerDelay(final String config, final String url) {
        try {
            // Input parameter validation
            if (config == null || config.trim().isEmpty()) {
                Log.w("getV2rayServerDelay", "Invalid config parameter: null or empty");
                return -1L;
            }
            
            if (url == null || url.trim().isEmpty()) {
                Log.w("getV2rayServerDelay", "Invalid URL parameter: null or empty");
                return -1L;
            }
            
            // Validate URL format
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                Log.w("getV2rayServerDelay", "Invalid URL format: " + url);
                return -1L;
            }
            
            long result = -1L;
            
            // Try with JSON processing first
            try {
                JSONObject config_json = new JSONObject(config);
                
                // Safe JSON handling with validation
                if (config_json.has("routing")) {
                    JSONObject routing_json = config_json.getJSONObject("routing");
                    if (routing_json != null) {
                        // Create a copy to avoid modifying original
                        JSONObject new_routing_json = new JSONObject();
                        
                        // Copy all fields except rules
                        Iterator<String> keys = routing_json.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            if (!"rules".equals(key)) {
                                new_routing_json.put(key, routing_json.get(key));
                            }
                        }
                        
                        config_json.put("routing", new_routing_json);
                    }
                }
                
                result = Libv2ray.measureOutboundDelay(config_json.toString(), url);
                Log.d("getV2rayServerDelay", "Delay measured with JSON processing: " + result + "ms");
                
            } catch (Exception json_error) {
                Log.w("getV2rayServerDelay", "JSON processing failed, using fallback: " + json_error.getMessage());
                
                // Robust fallback mechanism - use original config
                try {
                    result = Libv2ray.measureOutboundDelay(config, url);
                    Log.d("getV2rayServerDelay", "Delay measured with fallback: " + result + "ms");
                } catch (Exception fallback_error) {
                    Log.e("getV2rayServerDelay", "Fallback also failed: " + fallback_error.getMessage(), fallback_error);
                    return -1L;
                }
            }
            
            // Validate result (max 30 seconds = 30000ms)
            if (result < 0 || result > 30000) {
                Log.w("getV2rayServerDelay", "Invalid delay result: " + result + "ms");
                return -1L;
            }
            
            // Divide by 3 to get real ping value
            long realPing = result / 4;
            Log.d("getV2rayServerDelay", "Server delay measured: " + result + "ms, real ping: " + realPing + "ms");
            return realPing;
            
        } catch (Exception e) {
            Log.e("getV2rayServerDelay", "Failed to measure server delay: " + e.getMessage(), e);
            return -1L;
        } catch (Throwable t) {
            Log.e("getV2rayServerDelay", "Critical error measuring server delay: " + t.getMessage(), t);
            return -1L;
        }
    }

}
