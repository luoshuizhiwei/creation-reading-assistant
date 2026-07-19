/* SPDX-License-Identifier: GPL-3.0-only */
package local.creationReadingAssistant.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** Crash-safe checkpoints for native reader position, settings and session time. */
final class NativeReaderCheckpointJournal {
  private static final String PREFS_NAME = "native_reader_checkpoint_journal";
  private static final String KEY_CHECKPOINTS = "pending_checkpoints";
  private static final int MAX_CHECKPOINTS = 20;

  private NativeReaderCheckpointJournal() {}

  static synchronized void save(Context context, JSONObject checkpoint) throws JSONException {
    String sessionId = checkpoint.optString("sessionId", "");
    if (sessionId.isBlank()) throw new JSONException("checkpoint sessionId is required");
    JSONArray current = read(context);
    JSONArray next = new JSONArray();
    for (int index = 0; index < current.length(); index += 1) {
      JSONObject item = current.optJSONObject(index);
      if (item == null || sessionId.equals(item.optString("sessionId", ""))) continue;
      next.put(item);
    }
    next.put(new JSONObject(checkpoint.toString()));
    while (next.length() > MAX_CHECKPOINTS) {
      JSONArray trimmed = new JSONArray();
      for (int index = 1; index < next.length(); index += 1) trimmed.put(next.opt(index));
      next = trimmed;
    }
    preferences(context).edit().putString(KEY_CHECKPOINTS, next.toString()).commit();
  }

  static synchronized JSONArray read(Context context) {
    String raw = preferences(context).getString(KEY_CHECKPOINTS, "[]");
    try {
      return new JSONArray(raw == null ? "[]" : raw);
    } catch (JSONException error) {
      preferences(context).edit().remove(KEY_CHECKPOINTS).commit();
      return new JSONArray();
    }
  }

  static synchronized void acknowledge(Context context, JSONArray sessionIds) {
    Set<String> acknowledged = new HashSet<>();
    for (int index = 0; index < sessionIds.length(); index += 1) {
      String id = sessionIds.optString(index, "");
      if (!id.isBlank()) acknowledged.add(id);
    }
    if (acknowledged.isEmpty()) return;
    JSONArray current = read(context);
    JSONArray remaining = new JSONArray();
    for (int index = 0; index < current.length(); index += 1) {
      JSONObject item = current.optJSONObject(index);
      if (item == null || acknowledged.contains(item.optString("sessionId", ""))) continue;
      remaining.put(item);
    }
    preferences(context).edit().putString(KEY_CHECKPOINTS, remaining.toString()).commit();
  }

  private static SharedPreferences preferences(Context context) {
    return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
  }
}
