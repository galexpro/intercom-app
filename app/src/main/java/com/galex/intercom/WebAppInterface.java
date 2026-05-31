package com.galex.intercom;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;

public class WebAppInterface {
    private static final String TAG = "IntercomApp";
    private final Context context;
    private AudioRecord audioRecord;
    private Thread recordThread;
    private volatile boolean recording = false;
    private WebViewCallback callback;

    public interface WebViewCallback {
        void onAudioData(String base64Data);
    }

    public WebAppInterface(Context context) {
        this.context = context;
    }

    public void setCallback(WebViewCallback cb) {
        this.callback = cb;
    }

    @JavascriptInterface
    public String getServerUrl() {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        return prefs.getString(MainActivity.KEY_SERVER, MainActivity.DEFAULT_SERVER);
    }

    @JavascriptInterface
    public void setServerUrl(String url) {
        context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            .edit().putString(MainActivity.KEY_SERVER, url).apply();
    }

    @JavascriptInterface
    public void log(String msg) {
        Log.d(TAG, "JS: " + msg);
    }

    @JavascriptInterface
    public boolean startMic() {
        if (recording) return true;
        int sampleRate = 16000;
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2;
        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            audioRecord.startRecording();
            recording = true;
            Log.d(TAG, "Mic started, sampleRate=" + sampleRate + " bufSize=" + bufferSize);
            recordThread = new Thread(() -> {
                byte[] buf = new byte[bufferSize / 4];
                while (recording) {
                    int read = audioRecord.read(buf, 0, buf.length);
                    if (read > 0 && callback != null) {
                        String b64 = Base64.encodeToString(buf, 0, read, Base64.NO_WRAP);
                        callback.onAudioData(b64);
                    }
                }
            });
            recordThread.start();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "startMic error: " + e.getMessage());
            return false;
        }
    }

    @JavascriptInterface
    public void stopMic() {
        recording = false;
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        Log.d(TAG, "Mic stopped");
    }
}
