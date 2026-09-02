package com.example.set;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CommandPoller {
    private static final String TAG = "CommandPoller";

    private final String serverUrl = "http://192.168.0.101:8080";
    private final String phoneIp = "192.168.0.55";
    private final OkHttpClient client;
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Context context;

    private volatile boolean isRunning = false;

    public CommandPoller(Context context) {
        this.context = context;
        client = new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        Log.d(TAG, "Polling started");
        poll();
    }

    public void stop() {
        isRunning = false;
        Log.d(TAG, "Polling stopped");
        client.dispatcher().executorService().shutdown();
    }

    private void poll() {
        if (!isRunning) return;

        String url = serverUrl + "/commands?ip=" + phoneIp;
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!isRunning) return;

                if (response.isSuccessful()) {
                    String json = response.body().string();
                    if (json == null || json.trim().isEmpty()) {
                        Log.w(TAG, "Empty response from server");
                        scheduleNextPoll();
                        return;
                    }

                    Log.d(TAG, "Response JSON length: " + json.length());

                    try {
                        Type listType = new TypeToken<List<Command>>() {}.getType();
                        List<Command> commands = gson.fromJson(json, listType);

                        if (commands == null) {
                            scheduleNextPoll();
                            return;
                        }

                        for (Command cmd : commands) {
                            if (cmd == null || cmd.done || cmd.cmd == null) continue;
                            markDone(cmd.id);

                            if (cmd.cmd.startsWith("show_toast:")) {
                                String message = cmd.cmd.substring("show_toast:".length());
                                mainHandler.post(() -> {
                                    if (context instanceof android.app.Activity) {
                                        android.app.Activity activity = (android.app.Activity) context;
                                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                                            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                                        }
                                    } else {
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                                    }
                                });
                            }

                            else if (cmd.cmd.startsWith("open_tg_app")) {
                                boolean launched = false;

                                String packageName = "org.telegram.messenger";
                                Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);

                                Log.d(TAG, "Package name used: " + packageName);
                                Log.d(TAG, "Intent result: " + (intent == null ? "NULL (app not found)" : "OK"));

                                if (intent != null) {
                                    Log.d(TAG, "Activity resolved: " + intent.getComponent().toString());
                                }


                                if (intent != null) {
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    try {
                                        context.startActivity(intent);
                                        Log.d(TAG, "Telegram launched via package: " + packageName);
                                        launched = true;
                                    } catch (Exception e) {
                                        Log.e(TAG, "Failed to start via package", e);
                                    }
                                }

                                if (!launched) {
                                    String tgUrl = "tg://resolve?domain=durov";
                                    Intent tgIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(tgUrl));
                                    tgIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                                    if (tgIntent.resolveActivity(context.getPackageManager()) != null) {
                                        try {
                                            context.startActivity(tgIntent);
                                            Log.d(TAG, "Telegram launched via tg://resolve");
                                            launched = true;
                                        } catch (Exception e) {
                                            Log.e(TAG, "Failed to start via tg://", e);
                                        }
                                    }
                                }

                                if (!launched) {
                                    Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/"));
                                    browser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    try {
                                        context.startActivity(browser);
                                        Log.w(TAG, "Telegram not found, opening browser fallback.");
                                    } catch (Exception e) {
                                        Log.e(TAG, "Failed to open browser", e);
                                    }
                                }
                            }

                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing JSON", e);
                    }
                } else {
                    Log.w(TAG, "Server returned non-200: " + response.code());
                }

                scheduleNextPoll();
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Network error", e);
                if (isRunning) {
                    scheduleNextPoll();
                }
            }
        });
    }

    private void scheduleNextPoll() {
        if (isRunning) {
            mainHandler.postDelayed(() -> poll(), 5000);
        }
    }

    private void markDone(int cmdId) {
        String url = serverUrl + "/done";

        String jsonBody = "{\"cmd_id\": " + cmdId + "}";

        RequestBody body = RequestBody.create(
                okhttp3.MediaType.parse("application/json; charset=utf-8"),
                jsonBody
        );

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.d(TAG, "✅ /done успешно обработан сервером (status " + response.code() + ")");
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "";
                    Log.e(TAG, "❌ /done вернул ошибку: " + response.code() + " | " + errorBody);
                }

                if (response.body() != null) response.body().close();
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "❌ Ошибка сети при отправке /done: " + e.getMessage());
            }
        });
    }


    public static class Command {
        public int id;
        public String cmd;
        public boolean done = false;
    }
}