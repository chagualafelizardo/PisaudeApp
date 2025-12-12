package com.example.pisaudeapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class AutoService extends Service {

    private static final String TAG = "AutoService";
    private static final String CHANNEL_ID = "AutoServiceChannel";
    private static final int NOTIFICATION_ID = 1001;

    private Handler handler;
    private Runnable autoTask;
    private int intervalMinutes = 15;
    private boolean isRunning = false;
    private int cycleCount = 0;
    // Adicionar estas variáveis na classe
    private long lastBroadcastTime = 0;
    private int broadcastCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🟢 Service onCreate");

        createNotificationChannel();
        handler = new Handler(Looper.getMainLooper());
        intervalMinutes = getInterval(this);

        Log.d(TAG, "✅ Serviço criado com intervalo: " + intervalMinutes + " minutos");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "🚀 Service onStartCommand recebido");

        if (intent != null && "STOP_AUTO_SERVICE".equals(intent.getAction())) {
            Log.d(TAG, "⏹️ Recebido comando para parar");
            stopSelf();
            return START_NOT_STICKY;
        }

        // **CRÍTICO**: Iniciar foreground service com tipo adequado
        try {
            Notification notification = buildNotification("Serviço automático ativo");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Usar tipo que não tem timeout curto
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                Log.d(TAG, "📱 Foreground service iniciado com DATA_SYNC (Android 10+)");
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForeground(NOTIFICATION_ID, notification);
                Log.d(TAG, "📱 Foreground service iniciado normal (Android 8+)");
            } else {
                startForeground(NOTIFICATION_ID, notification);
                Log.d(TAG, "📱 Foreground service iniciado (Android <8)");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao iniciar foreground service: " + e.getMessage());
            // Fallback: tentar iniciar sem tipo específico
            try {
                startForeground(NOTIFICATION_ID, buildNotification("Serviço automático ativo"));
                Log.d(TAG, "✅ Serviço iniciado com fallback");
            } catch (Exception ex) {
                Log.e(TAG, "❌ Falha crítica ao iniciar serviço", ex);
                return START_NOT_STICKY;
            }
        }

        if (!isRunning) {
            Log.d(TAG, "▶️ Iniciando ciclo automático pela primeira vez");
            isRunning = true;
            startAutoCycle();
        } else {
            Log.d(TAG, "🔄 Serviço já está rodando, apenas atualizando");
            updateNotification("Serviço ativo - Próximo ciclo em " + intervalMinutes + " minutos");
        }

        return START_STICKY;
    }

    private void startAutoCycle() {
        Log.d(TAG, "🔄 Iniciando ciclo automático com intervalo de " + intervalMinutes + " minutos");

        // Remover qualquer tarefa anterior
        if (autoTask != null) {
            handler.removeCallbacks(autoTask);
        }

        autoTask = new Runnable() {
            @Override
            public void run() {
                try {
                    cycleCount++;
                    Log.d(TAG, "🔄 CICLO AUTOMÁTICO #" + cycleCount + " iniciado");

                    // 1. Atualizar notificação
                    updateNotification("Executando ciclo #" + cycleCount);

                    // 2. Enviar broadcast para MainActivity
                    sendBroadcastToMainActivity();

                    // 3. Agendar próximo ciclo
                    scheduleNextCycle();

                } catch (Exception e) {
                    Log.e(TAG, "❌ Erro no ciclo automático: " + e.getMessage(), e);
                    // Tentar novamente em 1 minuto se houver erro
                    handler.postDelayed(this, 60 * 1000L);
                }
            }
        };

        // **ALTERAÇÃO**: Primeiro ciclo após 30 segundos para dar tempo
        handler.postDelayed(autoTask, 30000L);
        Log.d(TAG, "⏰ Primeiro ciclo agendado para daqui a 30 segundos");
    }

    // No AutoService.java, no método sendBroadcastToMainActivity():
    private void sendBroadcastToMainActivity() {
        try {
            // Evitar ciclos muito rápidos (mínimo 4 minutos entre ciclos)
            long now = System.currentTimeMillis();
            long timeSinceLastBroadcast = now - lastBroadcastTime;

            if (timeSinceLastBroadcast < 4 * 60 * 1000L && lastBroadcastTime > 0) {
                Log.d(TAG, "⏳ Aguardando - Último broadcast há " + (timeSinceLastBroadcast/1000) + "s");
                return;
            }

            lastBroadcastTime = now;

            Intent broadcast = new Intent("AUTO_CYCLE_ACTION");
            broadcast.putExtra("CYCLE_NUMBER", cycleCount);
            broadcast.putExtra("TIMESTAMP", now);

            // Adicionar informação para controle de duplicação
            broadcast.putExtra("SERVICE_ID", hashCode());
            broadcast.putExtra("BROADCAST_COUNT", broadcastCount++);

            Log.d(TAG, "📡 Enviando broadcast - Ciclo #" + cycleCount +
                    " (ID: " + hashCode() + ", Count: " + broadcastCount + ")");

            // Enviar com flags específicas
            broadcast.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            sendBroadcast(broadcast);

            Log.d(TAG, "✅ Broadcast enviado - Ciclo #" + cycleCount);

        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao enviar broadcast", e);
        }
    }


    private void scheduleNextCycle() {
        long intervalMillis = intervalMinutes * 60 * 1000L;

        // Garantir intervalo mínimo de 5 minutos
        if (intervalMillis < 5 * 60 * 1000L) {
            intervalMillis = 5 * 60 * 1000L;
            Log.w(TAG, "⚠️ Intervalo ajustado para mínimo de 5 minutos");
        }

        Log.d(TAG, "⏰ Agendando próximo ciclo em " + (intervalMillis/60000) + " minutos");

        // Atualizar notificação
        updateNotification("Próximo ciclo em " + (intervalMillis/60000) + " minutos");

        // Limpar qualquer callback anterior
        if (autoTask != null && handler != null) {
            handler.removeCallbacks(autoTask);
        }

        // Agendar próximo ciclo
        if (handler != null) {
            handler.postDelayed(() -> {
                if (isRunning) {
                    cycleCount++;
                    Log.d(TAG, "🔄 CICLO AUTOMÁTICO #" + cycleCount + " iniciado");
                    updateNotification("Executando ciclo #" + cycleCount);
                    sendBroadcastToMainActivity();
                    scheduleNextCycle();
                }
            }, intervalMillis);
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "🔴 Service onDestroy");

        isRunning = false;

        if (autoTask != null && handler != null) {
            handler.removeCallbacks(autoTask);
        }

        try {
            Intent broadcast = new Intent("AUTO_SERVICE_STOPPED");
            sendBroadcast(broadcast);
            Log.d(TAG, "📡 Broadcast AUTO_SERVICE_STOPPED enviado");
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao enviar broadcast de parada", e);
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Serviço Automático PISaude",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Serviço que envia SMS periodicamente");
                channel.setShowBadge(false);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                    Log.d(TAG, "📱 Canal de notificação criado");
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Erro ao criar canal de notificação", e);
            }
        }
    }

    private Notification buildNotification(String status) {
        try {
            Intent notificationIntent = new Intent(this, MainActivity.class);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );

            int icon = getResources().getIdentifier("ic_notification", "drawable", getPackageName());
            if (icon == 0) {
                icon = android.R.drawable.ic_dialog_info;
            }

            return new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("PISaude - Serviço Ativo")
                    .setContentText("Status: " + status)
                    .setSmallIcon(icon)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setOnlyAlertOnce(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .build();
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao construir notificação", e);
            return null;
        }
    }

    private void updateNotification(String status) {
        try {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                Notification notification = buildNotification(status);
                if (notification != null) {
                    notificationManager.notify(NOTIFICATION_ID, notification);
                    Log.d(TAG, "📱 Notificação atualizada: " + status);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao atualizar notificação", e);
        }
    }

    // Métodos estáticos para controle do intervalo
    public static void updateInterval(Context context, int minutes) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
            prefs.edit().putInt("interval_minutes", minutes).apply();
            Log.d(TAG, "🕐 Intervalo atualizado para: " + minutes + " minutos");
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao atualizar intervalo", e);
        }
    }

    public static int getInterval(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
            return prefs.getInt("interval_minutes", 15);
        } catch (Exception e) {
            Log.e(TAG, "❌ Erro ao obter intervalo, usando padrão 15", e);
            return 15;
        }
    }
}