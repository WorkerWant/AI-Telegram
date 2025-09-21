package org.telegram.messenger;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class GPTApiClient {

    private static final String TAG = "GPTApiClient";
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final int TIMEOUT_MS = 30000;

    public static final int RESPONSE_SIZE_SMALL = 0;
    public static final int RESPONSE_SIZE_MEDIUM = 1;
    public static final int RESPONSE_SIZE_LARGE = 2;
    public static final String MODEL_GPT_35_TURBO = "gpt-3.5-turbo";
    public static final String MODEL_GPT_4 = "gpt-4o";
    public static final String MODEL_GPT_4_TURBO = "gpt-4-turbo-preview";

    public interface GPTCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    public static void transcribeAudio(java.io.File audioFile, String language, GPTCallback callback) {
        if (audioFile == null || !audioFile.exists()) {
            if (callback != null) callback.onError("Audio file not found");
            return;
        }
        if (TextUtils.isEmpty(SharedConfig.aiApiToken)) {
            if (callback != null) callback.onError("API token not set");
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            String boundary = "----TGWhisper" + System.currentTimeMillis();
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://api.openai.com/v1/audio/transcriptions");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Authorization", "Bearer " + SharedConfig.aiApiToken);
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setDoOutput(true);

                try (java.io.OutputStream os = connection.getOutputStream();
                     java.io.DataOutputStream dos = new java.io.DataOutputStream(os);
                     java.io.FileInputStream fis = new java.io.FileInputStream(audioFile)) {

                    writeFormField(dos, boundary, "model", "whisper-1");

                    if (!TextUtils.isEmpty(language)) {
                        writeFormField(dos, boundary, "language", language);
                    }

                    String fileName = audioFile.getName();
                    dos.writeBytes("--" + boundary + "\r\n");
                    dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n");
                    dos.writeBytes("Content-Type: application/octet-stream\r\n\r\n");

                    byte[] buffer = new byte[8192];
                    int len;

                    while ((len = fis.read(buffer)) != -1) {
                        dos.write(buffer, 0, len);
                    }

                    dos.writeBytes("\r\n");
                    dos.writeBytes("--" + boundary + "--\r\n");
                    dos.flush();
                }

                int responseCode = connection.getResponseCode();
                java.io.InputStream is = responseCode == HttpURLConnection.HTTP_OK ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder sb = new StringBuilder();
                try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, "utf-8"))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line.trim());
                    }
                }
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    String json = sb.toString();
                    try {
                        JSONObject obj = new JSONObject(json);
                        final String text = obj.optString("text", "");
                        if (callback != null) AndroidUtilities.runOnUIThread(() -> callback.onSuccess(text));
                    } catch (Exception pe) {
                        if (callback != null) AndroidUtilities.runOnUIThread(() -> callback.onError("Parse error"));
                    }
                } else {
                    String err = "HTTP " + responseCode + ": " + sb.toString();
                    if (callback != null) AndroidUtilities.runOnUIThread(() -> callback.onError(err));
                }
            } catch (Exception e) {
                if (BuildVars.LOGS_ENABLED) {
                    Log.e(TAG, "Whisper request failed", e);
                }
                if (callback != null) AndroidUtilities.runOnUIThread(() -> callback.onError(e.getMessage()));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static void writeFormField(java.io.DataOutputStream dos, String boundary, String name, String value) throws Exception {
        dos.writeBytes("--" + boundary + "\r\n");
        dos.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        dos.writeBytes(value + "\r\n");
    }

    public static String sendRequest(String prompt) {
        return sendRequest(prompt, null, SharedConfig.aiModel == 0 ? MODEL_GPT_35_TURBO : MODEL_GPT_4);
    }

    public static String sendRequest(String systemPrompt, String userPrompt) {
        return sendRequest(userPrompt, systemPrompt, SharedConfig.aiModel == 0 ? MODEL_GPT_35_TURBO : MODEL_GPT_4);
    }

    public static String sendRequest(String userPrompt, String systemPrompt, String model) {
        if (TextUtils.isEmpty(SharedConfig.aiApiToken)) {
            if (BuildVars.LOGS_ENABLED) {
                Log.e(TAG, "API token not set");
            }
            return null;
        }

        final String[] result = new String[1];
        final CountDownLatch latch = new CountDownLatch(1);

        sendRequestAsync(userPrompt, systemPrompt, model, new GPTCallback() {
            @Override
            public void onSuccess(String response) {
                result[0] = response;
                latch.countDown();
            }

            @Override
            public void onError(String error) {
                if (BuildVars.LOGS_ENABLED) {
                    Log.e(TAG, "Request error: " + error);
                }
                latch.countDown();
            }
        });

        try {
            boolean completed = latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!completed) {
                if (BuildVars.LOGS_ENABLED) {
                    Log.e(TAG, "Request timeout after " + TIMEOUT_MS + "ms");
                }
                return null;
            }
        } catch (InterruptedException e) {
            if (BuildVars.LOGS_ENABLED) {
                Log.e(TAG, "Request interrupted", e);
            }
            return null;
        }
        
        return result[0];
    }

    public static void sendRequestAsync(String userPrompt, String systemPrompt, String model, GPTCallback callback) {
        if (TextUtils.isEmpty(SharedConfig.aiApiToken)) {
            if (callback != null) {
                callback.onError("API token not set");
            }
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(API_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + SharedConfig.aiApiToken);
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setDoOutput(true);

                JSONObject requestJson = new JSONObject();
                requestJson.put("model", model);

                JSONArray messages = new JSONArray();
                if (!TextUtils.isEmpty(systemPrompt)) {
                    JSONObject systemMessage = new JSONObject();
                    systemMessage.put("role", "system");
                    systemMessage.put("content", systemPrompt);
                    messages.put(systemMessage);
                }

                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                userMessage.put("content", userPrompt);
                messages.put(userMessage);

                requestJson.put("messages", messages);

                switch (SharedConfig.aiResponseSize) {
                    case RESPONSE_SIZE_SMALL:
                        requestJson.put("max_tokens", 100);
                        requestJson.put("temperature", 0.7);
                        break;
                    case RESPONSE_SIZE_LARGE:
                        requestJson.put("max_tokens", 500);
                        requestJson.put("temperature", 0.9);
                        break;
                    default:
                        requestJson.put("max_tokens", 250);
                        requestJson.put("temperature", 0.8);
                        break;
                }

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = requestJson.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    StringBuilder response = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }
                    }

                    JSONObject responseJson = new JSONObject(response.toString());
                    JSONArray choices = responseJson.getJSONArray("choices");
                    if (choices.length() > 0) {
                        JSONObject choice = choices.getJSONObject(0);
                        JSONObject message = choice.getJSONObject("message");
                        String content = message.getString("content");

                        if (callback != null) {
                            AndroidUtilities.runOnUIThread(() -> callback.onSuccess(content));
                        }
                    } else {
                        if (callback != null) {
                            AndroidUtilities.runOnUIThread(() -> callback.onError("No response from GPT"));
                        }
                    }
                } else {
                    StringBuilder errorResponse = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            errorResponse.append(responseLine.trim());
                        }
                    }

                    String error = "HTTP " + responseCode + ": " + errorResponse;
                    if (BuildVars.LOGS_ENABLED) {
                        Log.e(TAG, "GPT API error: " + error);
                    }

                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onError(error));
                    }
                }

            } catch (Exception e) {
                if (BuildVars.LOGS_ENABLED) {
                    Log.e(TAG, "Request failed", e);
                }
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(() -> callback.onError(e.getMessage()));
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    

    public static final void summarizeVoiceMessageWithSettings(String transcription, String systemPrompt, int maxTokens, GPTCallback callback) {
        String languageInstruction;
        switch (SharedConfig.aiOutputLanguage) {
            case 1:
                languageInstruction = "Respond ONLY in English. ";
                break;
            case 2:
                languageInstruction = "Respond ONLY in Ukrainian (українською мовою). ";
                break;
            case 3:
                languageInstruction = "Respond ONLY in Russian (на русском языке). ";
                break;
            case 4:
                languageInstruction = "Respond ONLY in Spanish (en español). ";
                break;
            default:
                languageInstruction = "Detect the language of the input text and respond in the same language. ";
                break;
        }

        String lengthInstruction = "";
        switch (SharedConfig.aiResponseSize) {
            case RESPONSE_SIZE_SMALL:
                lengthInstruction = "Keep the summary very brief (1 sentence, max 15 words). ";
                break;
            case RESPONSE_SIZE_MEDIUM:
                lengthInstruction = "Provide a moderate summary (2-3 sentences). ";
                break;
            case RESPONSE_SIZE_LARGE:
                lengthInstruction = "Provide a detailed summary (3-5 sentences). ";
                break;
        }

        String finalSystemPrompt = (languageInstruction + (TextUtils.isEmpty(systemPrompt) ? "" : systemPrompt + " ") + lengthInstruction).trim();
        String model;
        int vmSel2 = SharedConfig.aiVoiceModel;
        if (vmSel2 == 0) {
            model = MODEL_GPT_35_TURBO;
        } else if (vmSel2 == 1) {
            model = MODEL_GPT_4;
        } else if (vmSel2 == 2) {
            model = MODEL_GPT_4_TURBO;
        } else {
            model = SharedConfig.aiModel == 0 ? MODEL_GPT_35_TURBO : MODEL_GPT_4;
        }
        
        sendRequestAsyncWithTokens(transcription, finalSystemPrompt, model, maxTokens, callback);
    }

    private static void sendRequestAsyncWithTokens(String userPrompt, String systemPrompt, String model, int maxTokens, GPTCallback callback) {
        if (TextUtils.isEmpty(SharedConfig.aiApiToken)) {
            if (callback != null) {
                callback.onError("API token not set");
            }
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(API_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + SharedConfig.aiApiToken);
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setDoOutput(true);

                JSONObject requestJson = new JSONObject();
                requestJson.put("model", model);
                int limit = 4096;
                int safeMax = Math.max(1, Math.min(maxTokens, limit));
                requestJson.put("max_tokens", safeMax);
                requestJson.put("temperature", 0.7);

                JSONArray messages = new JSONArray();
                if (!TextUtils.isEmpty(systemPrompt)) {
                    JSONObject systemMessage = new JSONObject();
                    systemMessage.put("role", "system");
                    systemMessage.put("content", systemPrompt);
                    messages.put(systemMessage);
                }

                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                userMessage.put("content", userPrompt);
                messages.put(userMessage);

                requestJson.put("messages", messages);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = requestJson.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    StringBuilder response = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }
                    }

                    JSONObject responseJson = new JSONObject(response.toString());
                    JSONArray choices = responseJson.getJSONArray("choices");
                    if (choices.length() > 0) {
                        JSONObject choice = choices.getJSONObject(0);
                        JSONObject message = choice.getJSONObject("message");
                        String content = message.getString("content");

                        if (callback != null) {
                            AndroidUtilities.runOnUIThread(() -> callback.onSuccess(content));
                        }
                    } else {
                        if (callback != null) {
                            AndroidUtilities.runOnUIThread(() -> callback.onError("No response from GPT"));
                        }
                    }
                } else {
                    StringBuilder errorResponse = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream(), "utf-8"))) {
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            errorResponse.append(responseLine.trim());
                        }
                    }

                    String error = "HTTP " + responseCode + ": " + errorResponse;
                    if (BuildVars.LOGS_ENABLED) {
                        Log.e(TAG, "GPT API error: " + error);
                    }

                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onError(error));
                    }
                }

            } catch (Exception e) {
                if (BuildVars.LOGS_ENABLED) {
                    Log.e(TAG, "Request failed", e);
                }
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(() -> callback.onError(e.getMessage()));
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    public static final void generateAutoResponse(List<String> messages, GPTCallback callback) {
        String systemPrompt = "You are helping the user respond to messages naturally. " +
                             "Generate an appropriate response based on the conversation context. " +
                             "Keep the response conversational and match the tone of the conversation.";

        StringBuilder userPrompt = new StringBuilder("Generate a response for this conversation:\n\n");
        for (int i = 0; i < messages.size(); i++) {
            userPrompt.append(messages.get(i)).append("\n");
        }

        String model = SharedConfig.aiModel == 0 ? MODEL_GPT_35_TURBO : MODEL_GPT_4;
        sendRequestAsync(userPrompt.toString(), systemPrompt, model, callback);
    }

    public static void testConnection(String apiKey, GPTCallback callback) {
        final String testApiKey = !TextUtils.isEmpty(apiKey) ? apiKey : SharedConfig.aiApiToken;

        if (TextUtils.isEmpty(testApiKey)) {
            if (callback != null) {
                callback.onError("No API key provided");
            }
            return;
        }

        final long startTime = System.currentTimeMillis();

        Utilities.globalQueue.postRunnable(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(API_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + testApiKey);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);

                JSONObject requestJson = getJsonObject();

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = requestJson.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    StringBuilder response = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), "utf-8"))) {
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }
                    }

                    JSONObject responseJson = new JSONObject(response.toString());

                    String model = responseJson.optString("model", MODEL_GPT_35_TURBO);

                    JSONArray choices = responseJson.getJSONArray("choices");
                    if (choices.length() > 0) {
                        long responseTime = System.currentTimeMillis() - startTime;

                        JSONObject testResult = new JSONObject();
                        testResult.put("status", "success");
                        testResult.put("model", model);
                        testResult.put("responseTime", responseTime);
                        testResult.put("message", "Connection successful!");

                        if (callback != null) {
                            AndroidUtilities.runOnUIThread(() -> callback.onSuccess(testResult.toString()));
                        }
                    } else {
                        if (callback != null) {
                            AndroidUtilities.runOnUIThread(() -> callback.onError("No response from API"));
                        }
                    }
                } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onError("Invalid API key"));
                    }
                } else if (responseCode == 429) {
                    if (callback != null) {
                        AndroidUtilities.runOnUIThread(() -> callback.onError("Rate limit exceeded"));
                    }
                } else {
                    StringBuilder errorResponse = new StringBuilder();
                    if (connection.getErrorStream() != null) {
                        try (BufferedReader br = new BufferedReader(
                                new InputStreamReader(connection.getErrorStream(), "utf-8"))) {
                            String responseLine;
                            while ((responseLine = br.readLine()) != null) {
                                errorResponse.append(responseLine.trim());
                            }
                        }
                    }

                    String error = "HTTP " + responseCode;
                    if (errorResponse.length() > 0) {
                        try {
                            JSONObject errorJson = new JSONObject(errorResponse.toString());
                            JSONObject errorObj = errorJson.optJSONObject("error");
                            if (errorObj != null) {
                                error = errorObj.optString("message", error);
                            }
                        } catch (Exception e) {
                        }
                    }

                    if (callback != null) {
                        final String finalError = error;
                        AndroidUtilities.runOnUIThread(() -> callback.onError(finalError));
                    }
                }

            } catch (Exception e) {
                String errorMessage = "Connection failed";
                if (e.getMessage() != null) {
                    if (e.getMessage().contains("Unable to resolve host")) {
                        errorMessage = "Network error: Check internet connection";
                    } else if (e.getMessage().contains("timeout")) {
                        errorMessage = "Connection timeout";
                    } else {
                        errorMessage = e.getMessage();
                    }
                }

                if (BuildVars.LOGS_ENABLED) {
                    Log.e(TAG, "Test connection failed", e);
                }

                if (callback != null) {
                    final String finalError = errorMessage;
                    AndroidUtilities.runOnUIThread(() -> callback.onError(finalError));
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    @NonNull
    private static JSONObject getJsonObject() throws JSONException {
        JSONObject requestJson = new JSONObject();
        requestJson.put("model", MODEL_GPT_35_TURBO);
        requestJson.put("max_tokens", 10);
        requestJson.put("temperature", 0.7);

        JSONArray messages = new JSONArray();
        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", "You are a test assistant. Reply with 'Connection successful!' to confirm the API is working.");
        messages.put(systemMessage);

        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", "Test connection");
        messages.put(userMessage);

        requestJson.put("messages", messages);
        return requestJson;
    }

}
