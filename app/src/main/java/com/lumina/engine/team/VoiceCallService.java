package com.lumina.engine.team;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.content.ContextCompat;
import androidx.core.app.NotificationCompat;

import com.lumina.engine.R;
import com.lumina.engine.ui.Tr;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;

public final class VoiceCallService extends Service {
    private static final String CHANNEL = "team_voice_call";
    private static final int NOTIFICATION_ID = 0x564F4943;
    private static final String ACTION_MUTE = "com.lumina.engine.team.voice.MUTE";
    private static final String ACTION_LEAVE = "com.lumina.engine.team.voice.LEAVE";
    private static WeakReference<VoiceCallService> instance;
    private final Handler main = new Handler(Looper.getMainLooper());

    public static void sync(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        if (!TeamSession.get().wantsVoice()) {
            app.stopService(new Intent(app, VoiceCallService.class));
            return;
        }
        VoiceCallService current = instance == null ? null : instance.get();
        if (current != null) {
            current.update();
            return;
        }
        try {
            ContextCompat.startForegroundService(app, new Intent(app, VoiceCallService.class));
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = new WeakReference<>(this);
        channel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_LEAVE.equals(action)) {
            TeamSession.get().leaveVoice();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_MUTE.equals(action)) TeamSession.get().toggleVoiceMute();
        if (!TeamSession.get().wantsVoice()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        update();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (TeamSession.get().inVoice()) TeamSession.get().leaveVoice();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void update() {
        if (Looper.myLooper() == Looper.getMainLooper()) updateNow();
        else main.post(this::updateNow);
    }

    private void updateNow() {
        TeamSession s = TeamSession.get();
        if (!s.wantsVoice()) {
            stopSelf();
            return;
        }
        Notification n = notification(s);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIFICATION_ID, n);
            }
        } catch (Throwable ignored) {
        }
    }

    private Notification notification(TeamSession s) {
        String title = Tr.t("team_call_title", "Llamada de equipo");
        TeamSettings.Membership m = s.membership();
        if (m != null && m.roomName != null && !m.roomName.trim().isEmpty()) title = m.roomName;
        int count = s.voiceMembers().size();
        if (count == 0 && s.inVoice()) count = 1;
        String text;
        if (!s.inVoice()) {
            text = Tr.t("team_call_reconnecting", "Reconectando…");
        } else if (s.voiceMuted()) {
            text = Tr.t("team_voice_muted", "Micrófono silenciado");
        } else if (count <= 1) {
            text = Tr.t("team_call_one", "1 persona en llamada");
        } else {
            text = String.format(Locale.getDefault(), Tr.t("team_call_many", "%d personas en llamada"), count);
        }
        String names = names(s);
        String mute = s.voiceMuted() ? Tr.t("team_voice_unmute", "Activar micrófono") : Tr.t("team_voice_mute", "Silenciar");
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_lumina_group)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(names.isEmpty() ? text : text + "\n" + names))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(openIntent())
                .addAction(R.drawable.ic_lumina_sound, mute, action(ACTION_MUTE, 1))
                .addAction(R.drawable.ic_lumina_close, Tr.t("team_voice_leave", "Salir de la llamada"), action(ACTION_LEAVE, 2));
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return b.build();
    }

    private String names(TeamSession s) {
        List<String> ids = s.voiceMembers();
        List<TeamClient.Member> members = s.members();
        StringBuilder out = new StringBuilder();
        for (String id : ids) {
            String name = null;
            for (TeamClient.Member member : members) {
                if (member != null && id != null && id.equals(member.id)) {
                    name = member.name;
                    break;
                }
            }
            if (name == null || name.trim().isEmpty()) name = id == null ? "" : id;
            if (out.length() > 0) out.append(", ");
            out.append(name);
        }
        return out.toString();
    }

    private PendingIntent openIntent() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent == null) return null;
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent action(String value, int code) {
        Intent intent = new Intent(this, VoiceCallService.class).setAction(value);
        return PendingIntent.getService(this, code, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void channel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                Tr.t("team_call_channel", "Llamadas de equipo"), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(Tr.t("team_call_channel_desc", "Mantiene la llamada activa mientras usas otras apps."));
        channel.setShowBadge(false);
        channel.enableVibration(false);
        channel.setSound(null, null);
        nm.createNotificationChannel(channel);
    }
}
