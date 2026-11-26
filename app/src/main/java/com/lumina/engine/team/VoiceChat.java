package com.lumina.engine.team;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Process;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VoiceChat {
    private static final int RATE = 16000;
    private static final int IN_CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int OUT_CHANNEL = AudioFormat.CHANNEL_OUT_MONO;
    private static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private final ExecutorService playback = Executors.newSingleThreadExecutor();
    private final Map<String, AudioTrack> callTracks = new HashMap<>();
    private final Map<String, Long> speakingAt = new ConcurrentHashMap<>();
    private AudioTrack track;
    private AudioRecord record;
    private AcousticEchoCanceler aec;
    private NoiseSuppressor noise;
    private AutomaticGainControl gain;
    private Thread capture;
    private volatile boolean talking;
    private volatile boolean inCall;
    private volatile boolean muted;
    private volatile boolean playing;
    private volatile boolean capturing;
    private volatile long localSpokeAt;

    public VoiceChat(Context context) {
    }

    public synchronized boolean startPlayback() {
        if (talking && !inCall) return false;
        if (track != null) {
            try {
                if (track.getState() == AudioTrack.STATE_INITIALIZED) {
                    track.play();
                    playing = true;
                    return true;
                }
            } catch (Throwable ignored) {
            }
            releaseTrack();
        }
        int min = AudioTrack.getMinBufferSize(RATE, OUT_CHANNEL, ENCODING);
        if (min <= 0) min = RATE;
        int size = Math.max(min, RATE / 5 * 2);
        try {
            track = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, OUT_CHANNEL, ENCODING, size, AudioTrack.MODE_STREAM);
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                releaseTrack();
                return false;
            }
            track.play();
            playing = true;
            return true;
        } catch (Throwable ignored) {
            releaseTrack();
            return false;
        }
    }

    public synchronized void stopPlayback() {
        playing = false;
        if (track != null) {
            try {
                track.pause();
                track.flush();
            } catch (Throwable ignored) {
            }
        }
    }

    public synchronized boolean startCall() {
        if (inCall) return true;
        talking = false;
        stopCapture();
        releaseCallTracks();
        speakingAt.clear();
        localSpokeAt = 0;
        inCall = true;
        muted = false;
        if (!startCapture()) {
            inCall = false;
            stopCapture();
            return false;
        }
        return true;
    }

    public synchronized void stopCall() {
        inCall = false;
        muted = false;
        localSpokeAt = 0;
        speakingAt.clear();
        stopCapture();
        releaseCallTracks();
        stopPlayback();
    }

    public synchronized boolean inCall() {
        return inCall;
    }

    public synchronized boolean muted() {
        return muted;
    }

    public synchronized boolean setMuted(boolean value) {
        if (muted == value) return true;
        muted = value;
        if (!inCall) return true;
        if (muted) {
            localSpokeAt = 0;
            stopCapture();
            return true;
        }
        if (!startCapture()) {
            muted = true;
            return false;
        }
        return true;
    }

    public synchronized void release() {
        talking = false;
        inCall = false;
        muted = false;
        localSpokeAt = 0;
        speakingAt.clear();
        stopCapture();
        playing = false;
        releaseCallTracks();
        releaseTrack();
    }

    public synchronized boolean startTalking() {
        if (inCall) return false;
        if (talking) return true;
        stopPlayback();
        if (!startCapture()) {
            if (TeamSession.get().isConnected()) startPlayback();
            return false;
        }
        talking = true;
        return true;
    }

    public synchronized void stopTalking() {
        talking = false;
        if (inCall) return;
        stopCapture();
        if (TeamSession.get().isConnected()) startPlayback();
    }

    public boolean localSpeaking() {
        return inCall && !muted && System.currentTimeMillis() - localSpokeAt < 450;
    }

    public boolean remoteSpeaking(String from) {
        if (!inCall || from == null) return false;
        Long at = speakingAt.get(from);
        return at != null && System.currentTimeMillis() - at < 450;
    }

    public void play(String from, byte[] data) {
        if (data == null || data.length == 0) return;
        if (!inCall) {
            playSingle(data);
            return;
        }
        final AudioTrack target;
        final String key;
        synchronized (this) {
            if (!inCall) return;
            key = from == null || from.isEmpty() ? "unknown" : from;
            AudioTrack existing = callTracks.get(key);
            if (existing == null) {
                existing = newTrack();
                if (existing == null) return;
                callTracks.put(key, existing);
            }
            target = existing;
            if (loud(data, data.length)) speakingAt.put(key, System.currentTimeMillis());
        }
        playback.execute(() -> {
            try {
                target.write(data, 0, data.length);
            } catch (Throwable ignored) {
            }
        });
    }

    private void playSingle(byte[] data) {
        if (talking) return;
        final AudioTrack target;
        synchronized (this) {
            target = track;
            if (target == null || !playing) return;
        }
        playback.execute(() -> {
            try {
                target.write(data, 0, data.length);
            } catch (Throwable ignored) {
            }
        });
    }

    private synchronized boolean startCapture() {
        if (capturing) return true;
        int min = AudioRecord.getMinBufferSize(RATE, IN_CHANNEL, ENCODING);
        if (min <= 0) min = RATE;
        int size = Math.max(min, RATE / 5 * 2);
        AudioRecord rec = openRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, size);
        if (rec == null) rec = openRecord(MediaRecorder.AudioSource.MIC, size);
        if (rec == null) return false;
        try {
            rec.startRecording();
        } catch (Throwable ignored) {
            releaseRecord();
            return false;
        }
        record = rec;
        capturing = true;
        final AudioRecord source = rec;
        final int bufferSize = Math.max(640, size / 2);
        capture = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            byte[] buffer = new byte[bufferSize];
            while (capturing && record == source) {
                int read;
                try {
                    read = source.read(buffer, 0, buffer.length);
                } catch (Throwable ignored) {
                    break;
                }
                if (read <= 0) {
                    if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) break;
                    continue;
                }
                if (muted) continue;
                if (loud(buffer, read)) localSpokeAt = System.currentTimeMillis();
                TeamSession.get().sendVoice(buffer, read);
            }
        }, "team-voice-mic");
        capture.start();
        return true;
    }

    private static boolean loud(byte[] data, int length) {
        long sum = 0;
        int samples = 0;
        for (int i = 0; i + 1 < length; i += 2) {
            int sample = (short) ((data[i] & 0xff) | (data[i + 1] << 8));
            sum += (long) sample * sample;
            samples++;
        }
        return samples > 0 && Math.sqrt((double) sum / samples) > 900;
    }

    private synchronized void stopCapture() {
        capturing = false;
        releaseRecord();
    }

    private AudioRecord openRecord(int source, int size) {
        try {
            AudioRecord rec = new AudioRecord(source, RATE, IN_CHANNEL, ENCODING, size);
            if (rec.getState() == AudioRecord.STATE_INITIALIZED) {
                enableEffects(rec);
                return rec;
            }
            try {
                rec.release();
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void enableEffects(AudioRecord rec) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(rec.getAudioSessionId());
                if (aec != null) aec.setEnabled(true);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (NoiseSuppressor.isAvailable()) {
                noise = NoiseSuppressor.create(rec.getAudioSessionId());
                if (noise != null) noise.setEnabled(true);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (AutomaticGainControl.isAvailable()) {
                gain = AutomaticGainControl.create(rec.getAudioSessionId());
                if (gain != null) gain.setEnabled(true);
            }
        } catch (Throwable ignored) {
        }
    }

    private void releaseEffects() {
        if (aec != null) {
            try {
                aec.setEnabled(false);
                aec.release();
            } catch (Throwable ignored) {
            }
            aec = null;
        }
        if (noise != null) {
            try {
                noise.setEnabled(false);
                noise.release();
            } catch (Throwable ignored) {
            }
            noise = null;
        }
        if (gain != null) {
            try {
                gain.setEnabled(false);
                gain.release();
            } catch (Throwable ignored) {
            }
            gain = null;
        }
    }

    private AudioTrack newTrack() {
        int min = AudioTrack.getMinBufferSize(RATE, OUT_CHANNEL, ENCODING);
        if (min <= 0) min = RATE;
        int size = Math.max(min, RATE / 5 * 2);
        try {
            AudioTrack t = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, OUT_CHANNEL, ENCODING, size, AudioTrack.MODE_STREAM);
            if (t.getState() != AudioTrack.STATE_INITIALIZED) {
                t.release();
                return null;
            }
            t.play();
            return t;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public synchronized void drop(String from) {
        if (from == null) return;
        speakingAt.remove(from);
        releaseOne(callTracks.remove(from));
    }

    private void releaseCallTracks() {
        for (AudioTrack t : callTracks.values()) releaseOne(t);
        callTracks.clear();
    }

    private void releaseOne(AudioTrack t) {
        if (t == null) return;
        try {
            t.pause();
            t.flush();
            t.release();
        } catch (Throwable ignored) {
        }
    }

    private void releaseRecord() {
        releaseEffects();
        AudioRecord rec = record;
        record = null;
        if (rec != null) {
            try {
                rec.stop();
            } catch (Throwable ignored) {
            }
            try {
                rec.release();
            } catch (Throwable ignored) {
            }
        }
        capture = null;
    }

    private void releaseTrack() {
        AudioTrack t = track;
        track = null;
        if (t != null) {
            try {
                t.pause();
                t.flush();
                t.release();
            } catch (Throwable ignored) {
            }
        }
    }
}
