package com.example.set;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class CommandService extends Service {
    private static final String TAG = "CommandService";
    private CommandPoller poller;
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "poller_channel";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called");

        Notification notification = createForegroundNotification();

        // Android 14+ требует явное указание foregroundServiceType
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        if (poller == null) {
            poller = new CommandPoller(this);
            poller.start();
        }

        return START_STICKY;
    }

    private Notification createForegroundNotification() {
        String channelName = "Poller Channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    channelName,
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Канал для фонового опроса сервера");
            channel.setShowBadge(false);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        // PendingIntent для открытия приложения по тапу на уведомление
        Intent tapIntent = new Intent(this, StartActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, tapIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Управление телефоном")
                .setContentText("Слушаю команды от ПК")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)           // нельзя смахнуть
                .setContentIntent(pi)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        if (poller != null) {
            poller.stop();
            poller = null;
        }
        super.onDestroy();
    }
}
