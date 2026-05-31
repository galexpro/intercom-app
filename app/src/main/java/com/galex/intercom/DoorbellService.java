package com.galex.intercom;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;
import java.net.URI;
import java.net.URISyntaxException;

public class DoorbellService extends Service {

    private static final String CHANNEL_ID = "intercom_channel";
    private static final String CHANNEL_RING_ID = "intercom_ring";
    private static final int NOTIF_ID = 1;
    private static final int RING_NOTIF_ID = 2;

    private SimpleWebSocket webSocket;
    private Handler handler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;
    private boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "intercom:doorbell");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildForegroundNotification());
        if (!running) {
            running = true;
            connectWebSocket();
        }
        return START_STICKY;
    }

    private void connectWebSocket() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String server = prefs.getString(MainActivity.KEY_SERVER, MainActivity.DEFAULT_SERVER);
        // Конвертируем http:// в ws://
        String wsUrl = server.replace("http://", "ws://").replace("https://", "wss://");
        // Порт doorbell WS
        String host = wsUrl.replaceAll(":[0-9]+$", "");
        String doorbellWs = host + ":7071";

        try {
            webSocket = new SimpleWebSocket(new URI(doorbellWs)) {
                @Override
                public void onMessage(String message) {
                    try {
                        JSONObject ev = new JSONObject(message);
                        String type = ev.optString("type");
                        if ("doorbell".equals(type) && ev.optBoolean("is_ring")) {
                            handler.post(() -> onDoorbell());
                        } else if ("doorbell_end".equals(type)) {
                            handler.post(() -> cancelRingNotification());
                        }
                    } catch (Exception e) {}
                }

                @Override
                public void onClose(int code, String reason) {
                    handler.postDelayed(() -> {
                        if (running) connectWebSocket();
                    }, 3000);
                }

                @Override
                public void onError(Exception e) {}
            };
            webSocket.connect();
        } catch (URISyntaxException e) {}
    }

    private void onDoorbell() {
        // Будим экран
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(30000);
        }

        // Запускаем MainActivity с флагом звонка
        Intent activityIntent = new Intent(this, MainActivity.class);
        activityIntent.setAction("DOORBELL");
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

        PendingIntent pi = PendingIntent.getActivity(this, 0, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Уведомление со звонком
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_RING_ID)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("Входящий вызов")
                .setContentText("Кто-то у двери")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                .setVibrate(new long[]{0, 500, 300, 500, 300, 500});

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(RING_NOTIF_ID, builder.build());

        // Открываем приложение
        startActivity(activityIntent);
    }

    private void cancelRingNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(RING_NOTIF_ID);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

            // Фоновый канал
            NotificationChannel bg = new NotificationChannel(CHANNEL_ID,
                    "Домофон фон", NotificationManager.IMPORTANCE_LOW);
            bg.setDescription("Фоновое подключение к домофону");
            nm.createNotificationChannel(bg);

            // Канал звонков
            NotificationChannel ring = new NotificationChannel(CHANNEL_RING_ID,
                    "Входящие звонки", NotificationManager.IMPORTANCE_HIGH);
            ring.setDescription("Уведомления о звонках");
            ring.enableVibration(true);
            ring.setVibrationPattern(new long[]{0, 500, 300, 500});
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            ring.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), aa);
            nm.createNotificationChannel(ring);
        }
    }

    private Notification buildForegroundNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Домофон активен")
                .setContentText("Ожидание звонков...")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        running = false;
        if (webSocket != null) webSocket.close();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
}
