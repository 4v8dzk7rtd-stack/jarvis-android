package com.jarvis.local;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class MainActivity extends Activity {

    private static final int SPEECH_REQUEST = 101;
    private static final int AUDIO_REQUEST = 102;

    private static final String CHAT_URL =
            "http://192.168.1.164:8765/chat";

    private static final String TTS_URL =
            "http://192.168.1.164:8765/tts";

    private static final String TOKEN = "jarvis-local";

    private TextView statusView;
    private TextView conversationView;
    private Button microphoneButton;
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildInterface();
    }

    private void buildInterface() {
        int pad = dp(24);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(5, 11, 18));

        TextView title = new TextView(this);
        title.setText("JARVIS");
        title.setTextColor(Color.rgb(80, 220, 255));
        title.setTextSize(34);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(70)));

        statusView = new TextView(this);
        statusView.setText("Готов");
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(19);
        statusView.setGravity(Gravity.CENTER);
        root.addView(statusView, new LinearLayout.LayoutParams(-1, dp(54)));

        microphoneButton = new Button(this);
        microphoneButton.setText("🎙\nГОВОРИТЬ");
        microphoneButton.setTextSize(22);
        microphoneButton.setTextColor(Color.WHITE);
        microphoneButton.setAllCaps(false);
        microphoneButton.setBackground(createRoundBackground());

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(dp(210), dp(210));

        buttonParams.setMargins(0, dp(18), 0, dp(24));
        root.addView(microphoneButton, buttonParams);

        microphoneButton.setOnClickListener(v -> startListening());

        ScrollView scroll = new ScrollView(this);

        conversationView = new TextView(this);
        conversationView.setText(
                "Нажми кнопку и задай вопрос Джарвису."
        );
        conversationView.setTextColor(Color.rgb(210, 244, 255));
        conversationView.setTextSize(17);
        conversationView.setPadding(
                dp(16), dp(16), dp(16), dp(16)
        );
        conversationView.setBackgroundColor(
                Color.rgb(12, 28, 40)
        );

        scroll.addView(conversationView);

        LinearLayout.LayoutParams scrollParams =
                new LinearLayout.LayoutParams(-1, 0, 1f);

        root.addView(scroll, scrollParams);
        setContentView(root);
    }

    private android.graphics.drawable.GradientDrawable
    createRoundBackground() {

        android.graphics.drawable.GradientDrawable drawable =
                new android.graphics.drawable.GradientDrawable();

        drawable.setShape(
                android.graphics.drawable.GradientDrawable.OVAL
        );
        drawable.setColor(Color.rgb(7, 79, 106));
        drawable.setStroke(
                dp(3), Color.rgb(38, 217, 255)
        );

        return drawable;
    }

    private void startListening() {
        if (android.os.Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{
                            Manifest.permission.RECORD_AUDIO
                    },
                    AUDIO_REQUEST
            );
            return;
        }

        Intent intent = new Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        );

        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        );
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "ru-RU"
        );
        intent.putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Говори..."
        );

        try {
            statusView.setText("Слушаю...");
            startActivityForResult(intent, SPEECH_REQUEST);
        } catch (Exception e) {
            statusView.setText("Ошибка микрофона");

            Toast.makeText(
                    this,
                    "На телефоне нет службы распознавания речи",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == SPEECH_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> results =
                        data.getStringArrayListExtra(
                                RecognizerIntent.EXTRA_RESULTS
                        );

                if (results != null && !results.isEmpty()) {
                    sendMessage(results.get(0));
                } else {
                    statusView.setText("Не расслышал");
                }
            } else {
                statusView.setText("Готов");
            }
        }
    }

    private void sendMessage(String message) {
        statusView.setText("Думаю...");
        microphoneButton.setEnabled(false);

        conversationView.setText(
                "Вы: " + message + "\n\nJARVIS: ..."
        );

        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(CHAT_URL);
                connection =
                        (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(180000);
                connection.setDoOutput(true);

                connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=utf-8"
                );
                connection.setRequestProperty(
                        "X-Jarvis-Token",
                        TOKEN
                );

                byte[] payload = new JSONObject()
                        .put("message", message)
                        .toString()
                        .getBytes(StandardCharsets.UTF_8);

                connection.setFixedLengthStreamingMode(
                        payload.length
                );

                try (OutputStream out =
                             connection.getOutputStream()) {
                    out.write(payload);
                }

                int code = connection.getResponseCode();

                InputStream stream =
                        code >= 200 && code < 300
                                ? connection.getInputStream()
                                : connection.getErrorStream();

                String body = readAll(stream);
                JSONObject json = new JSONObject(body);

                String reply = json.optString(
                        "reply",
                        json.optString(
                                "error",
                                "Нет ответа"
                        )
                );

                runOnUiThread(() ->
                        showReplyText(message, reply)
                );

                try {
                    File audioFile = requestSpeech(reply);

                    runOnUiThread(() ->
                            playSpeech(audioFile)
                    );

                } catch (Exception speechError) {
                    runOnUiThread(() -> {
                        statusView.setText("Готов");
                        microphoneButton.setEnabled(true);

                        Toast.makeText(
                                this,
                                "Ответ получен, но компьютер не создал голос",
                                Toast.LENGTH_LONG
                        ).show();
                    });
                }

            } catch (Exception e) {
                String error =
                        "Не удалось связаться с компьютером. " +
                        "Проверь Wi-Fi, сервер и адрес " +
                        "192.168.1.164:8765.";

                runOnUiThread(() ->
                        showError(message, error)
                );

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private File requestSpeech(String text) throws Exception {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(TTS_URL);
            connection =
                    (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(120000);
            connection.setDoOutput(true);

            connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
            );
            connection.setRequestProperty(
                    "X-Jarvis-Token",
                    TOKEN
            );
            connection.setRequestProperty(
                    "Accept",
                    "audio/wav"
            );

            byte[] payload = new JSONObject()
                    .put("text", text)
                    .toString()
                    .getBytes(StandardCharsets.UTF_8);

            connection.setFixedLengthStreamingMode(
                    payload.length
            );

            try (OutputStream out =
                         connection.getOutputStream()) {
                out.write(payload);
            }

            int code = connection.getResponseCode();

            if (code < 200 || code >= 300) {
                throw new Exception(
                        "Ошибка синтеза речи: " + code
                );
            }

            File audioFile = new File(
                    getCacheDir(),
                    "jarvis_reply_" +
                            System.currentTimeMillis() +
                            ".wav"
            );

            try (
                    InputStream input =
                            connection.getInputStream();
                    FileOutputStream output =
                            new FileOutputStream(audioFile)
            ) {
                byte[] buffer = new byte[8192];
                int count;

                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            }

            return audioFile;

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readAll(InputStream stream)
            throws Exception {

        if (stream == null) {
            return "{}";
        }

        StringBuilder result = new StringBuilder();

        try (BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(
                                     stream,
                                     StandardCharsets.UTF_8
                             )
                     )) {

            String line;

            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }

        return result.toString();
    }

    private void showReplyText(
            String message,
            String reply
    ) {
        conversationView.setText(
                "Вы: " + message +
                        "\n\nJARVIS: " + reply
        );

        statusView.setText("Готовлю голос...");
    }

    private void playSpeech(File audioFile) {
        stopCurrentSpeech();

        try {
            statusView.setText("Отвечаю...");

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(
                    audioFile.getAbsolutePath()
            );

            mediaPlayer.setOnPreparedListener(
                    MediaPlayer::start
            );

            mediaPlayer.setOnCompletionListener(mp ->
                    finishSpeech(audioFile)
            );

            mediaPlayer.setOnErrorListener(
                    (mp, what, extra) -> {
                        finishSpeech(audioFile);

                        Toast.makeText(
                                this,
                                "Не удалось воспроизвести голос",
                                Toast.LENGTH_LONG
                        ).show();

                        return true;
                    }
            );

            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            finishSpeech(audioFile);

            Toast.makeText(
                    this,
                    "Ошибка воспроизведения голоса",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void finishSpeech(File audioFile) {
        stopCurrentSpeech();

        if (audioFile != null && audioFile.exists()) {
            audioFile.delete();
        }

        statusView.setText("Готов");
        microphoneButton.setEnabled(true);
    }

    private void stopCurrentSpeech() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (Exception ignored) {
            }

            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void showError(
            String message,
            String error
    ) {
        conversationView.setText(
                "Вы: " + message +
                        "\n\nОшибка: " + error
        );

        statusView.setText("Нет соединения");
        microphoneButton.setEnabled(true);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == AUDIO_REQUEST
                && grantResults.length > 0
                && grantResults[0]
                == PackageManager.PERMISSION_GRANTED) {

            startListening();

        } else {
            Toast.makeText(
                    this,
                    "Без доступа к микрофону голосовой ввод не работает",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private int dp(int value) {
        return Math.round(
                value *
                getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    @Override
    protected void onDestroy() {
        stopCurrentSpeech();
        super.onDestroy();
    }
}
