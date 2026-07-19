/* SPDX-License-Identifier: GPL-3.0-only */
package local.creationReadingAssistant.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Crash-safe hand-off journal for actions created inside the native reader.
 *
 * The Capacitor WebView is paused while {@link NativeReaderActivity} is open,
 * so notes cannot be written to the React/SQLite store immediately. Keeping a
 * small native journal makes bookmarks, notes and inspirations recoverable if
 * Android kills the process before the reader activity returns.
 */
final class NativeReaderActionJournal {
  private static final String PREFS_NAME = "native_reader_action_journal";
  private static final String KEY_ACTIONS = "pending_actions";
  private static final int MAX_ACTIONS = 500;

  private NativeReaderActionJournal() {}

  static synchronized JSONObject append(Context context, JSONObject source) throws JSONException {
    JSONObject action = new JSONObject(source.toString());
    if (action.optString("actionId", "").isBlank()) {
      action.put("actionId", UUID.randomUUID().toString());
    }
    if (!action.has("createdAt")) action.put("createdAt", System.currentTimeMillis());

    JSONArray current = read(context);
    JSONArray next = new JSONArray();
    int start = Math.max(0, current.length() - MAX_ACTIONS + 1);
    for (int index = start; index < current.length(); index += 1) {
      JSONObject item = current.optJSONObject(index);
      if (item != null) next.put(item);
    }
    next.put(action);
    preferences(context).edit().putString(KEY_ACTIONS, next.toString()).commit();
    return action;
  }

  static synchronized JSONArray read(Context context) {
    String raw = preferences(context).getString(KEY_ACTIONS, "[]");
    try {
      return new JSONArray(raw == null ? "[]" : raw);
    } catch (JSONException error) {
      preferences(context).edit().remove(KEY_ACTIONS).commit();
      return new JSONArray();
    }
  }

  static synchronized void acknowledge(Context context, JSONArray actionIds) {
    Set<String> acknowledged = new HashSet<>();
    for (int index = 0; index < actionIds.length(); index += 1) {
      String id = actionIds.optString(index, "");
      if (!id.isBlank()) acknowledged.add(id);
    }
    if (acknowledged.isEmpty()) return;

    JSONArray current = read(context);
    JSONArray remaining = new JSONArray();
    for (int index = 0; index < current.length(); index += 1) {
      JSONObject action = current.optJSONObject(index);
      if (action == null || acknowledged.contains(action.optString("actionId", ""))) continue;
      remaining.put(action);
    }
    preferences(context).edit().putString(KEY_ACTIONS, remaining.toString()).commit();
  }

  private static SharedPreferences preferences(Context context) {
    return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
  }
}
