package com.example.set;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class CommandService extends Service {
    private static final String TAG = "CommandService";
    private CommandPoller poller;
    private static final int NOTIFICATION_ID = 1;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called");

        // Создаём постоянное уведомление (обязательно для Foreground Service)
        Notification notification = createForegroundNotification();
        startForeground(NOTIFICATION_ID, notification);

        // Инициализируем CommandPoller с контекстом сервиса (this)
        poller = new CommandPoller(this);
        poller.start();

        return START_STICKY;
    }

    private Notification createForegroundNotification() {
        String channelId = "poller_channel";
        String channelName = "Poller Channel";

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    channelName,
                    android.app.NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Канал для фонового опроса сервера");
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Poller работает")
                .setContentText("Опрос сервера каждые 5 сек")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Для Foreground Service обычно не нужен bind
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        if (poller != null) {
            poller.stop();
            poller = null;
        }
    }
}
