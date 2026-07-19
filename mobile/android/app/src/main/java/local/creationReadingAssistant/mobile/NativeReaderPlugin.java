/* SPDX-License-Identifier: GPL-3.0-only */
package local.creationReadingAssistant.mobile;

import android.app.Activity;
import android.content.Intent;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONException;

@CapacitorPlugin(name = "NativeReader")
public class NativeReaderPlugin extends Plugin {
  @PluginMethod
  public void isAvailable(PluginCall call) {
    JSObject result = new JSObject();
    result.put("available", true);
    result.put("engine", "native-legado");
    call.resolve(result);
  }

  @PluginMethod
  public void getPendingActions(PluginCall call) {
    JSObject result = new JSObject();
    result.put("actions", NativeReaderActionJournal.read(getContext()));
    call.resolve(result);
  }

  @PluginMethod
  public void acknowledgeActions(PluginCall call) {
    JSONArray actionIds = call.getData().optJSONArray("actionIds");
    if (actionIds == null) {
      call.reject("缺少要确认的原生阅读动作编号");
      return;
    }
    NativeReaderActionJournal.acknowledge(getContext(), actionIds);
    JSObject result = new JSObject();
    result.put("acknowledged", actionIds.length());
    call.resolve(result);
  }

  @PluginMethod
  public void getPendingCheckpoints(PluginCall call) {
    JSObject result = new JSObject();
    result.put("checkpoints", NativeReaderCheckpointJournal.read(getContext()));
    call.resolve(result);
  }

  @PluginMethod
  public void acknowledgeCheckpoints(PluginCall call) {
    JSONArray sessionIds = call.getData().optJSONArray("sessionIds");
    if (sessionIds == null) {
      call.reject("缺少要确认的原生阅读会话编号");
      return;
    }
    NativeReaderCheckpointJournal.acknowledge(getContext(), sessionIds);
    JSObject result = new JSObject();
    result.put("acknowledged", sessionIds.length());
    call.resolve(result);
  }

  @PluginMethod
  public void open(PluginCall call) {
    String bookId = call.getString("bookId");
    String title = call.getString("title");
    String fileUri = call.getString("fileUri");
    String format = call.getString("format", "txt");
    if (bookId == null || bookId.isBlank() || title == null || fileUri == null || fileUri.isBlank()) {
      call.reject("缺少原生阅读器所需的书籍信息或本地文件地址");
      return;
    }
    if (!"txt".equals(format) && !"md".equals(format) && !"epub".equals(format)) {
      call.reject("当前原生内核仅支持 TXT、Markdown 与 EPUB");
      return;
    }

    JSObject locator = call.getObject("locator", new JSObject());
    JSObject settings = call.getObject("settings", new JSObject());
    Intent intent = new Intent(getContext(), NativeReaderActivity.class);
    intent.putExtra(NativeReaderActivity.EXTRA_BOOK_ID, bookId);
    intent.putExtra(NativeReaderActivity.EXTRA_TITLE, title);
    intent.putExtra(NativeReaderActivity.EXTRA_AUTHOR, call.getString("author", ""));
    intent.putExtra(NativeReaderActivity.EXTRA_FILE_URI, fileUri);
    intent.putExtra(NativeReaderActivity.EXTRA_FORMAT, format);
    intent.putExtra(NativeReaderActivity.EXTRA_START_OFFSET, locator.optLong("charOffset", 0L));
    intent.putExtra(NativeReaderActivity.EXTRA_START_PAGE_INDEX, locator.optInt("pageIndex", 0));
    intent.putExtra(NativeReaderActivity.EXTRA_START_CHAPTER_INDEX, locator.optInt("chapterIndex", 0));
    intent.putExtra(NativeReaderActivity.EXTRA_START_EPUB_HREF, locator.optString("epubHref", ""));
    intent.putExtra(NativeReaderActivity.EXTRA_FONT_SIZE, settings.optDouble("fontSizeSp", 20.0));
    intent.putExtra(NativeReaderActivity.EXTRA_LINE_SPACING, settings.optDouble("lineSpacingMultiplier", 1.7));
    intent.putExtra(NativeReaderActivity.EXTRA_PARAGRAPH_SPACING, settings.optDouble("paragraphSpacingDp", 12.0));
    intent.putExtra(NativeReaderActivity.EXTRA_HORIZONTAL_PADDING, settings.optDouble("horizontalPaddingDp", 24.0));
    intent.putExtra(NativeReaderActivity.EXTRA_VERTICAL_PADDING, settings.optDouble("verticalPaddingDp", 28.0));
    intent.putExtra(NativeReaderActivity.EXTRA_TEXT_BOLD, settings.optBoolean("textBold", false));
    intent.putExtra(NativeReaderActivity.EXTRA_PAGE_MODE, settings.getString("pageMode", "SLIDE"));
    intent.putExtra(NativeReaderActivity.EXTRA_THEME, settings.getString("theme", "WARM"));
    startActivityForResult(call, intent, "readerResult");
  }

  @ActivityCallback
  private void readerResult(PluginCall call, ActivityResult activityResult) {
    Intent data = activityResult.getData();
    JSObject result = new JSObject();
    if (activityResult.getResultCode() != Activity.RESULT_OK || data == null) {
      result.put("cancelled", true);
      call.resolve(result);
      return;
    }
    result.put("cancelled", false);
    result.put("bookId", data.getStringExtra(NativeReaderActivity.RESULT_BOOK_ID));
    result.put("charOffset", data.getLongExtra(NativeReaderActivity.RESULT_CHAR_OFFSET, 0L));
    result.put("pageIndex", data.getIntExtra(NativeReaderActivity.RESULT_PAGE_INDEX, 0));
    result.put("progressPercent", data.getDoubleExtra(NativeReaderActivity.RESULT_PROGRESS, 0.0));
    result.put("activeDurationMs", data.getLongExtra(NativeReaderActivity.RESULT_ACTIVE_DURATION, 0L));
    result.put("sessionId", data.getStringExtra(NativeReaderActivity.RESULT_SESSION_ID));
    result.put("chapterTitle", data.getStringExtra(NativeReaderActivity.RESULT_CHAPTER_TITLE));
    result.put("chapterIndex", data.getIntExtra(NativeReaderActivity.RESULT_CHAPTER_INDEX, 0));
    result.put("epubHref", data.getStringExtra(NativeReaderActivity.RESULT_EPUB_HREF));
    try {
      result.put("actions", new JSONArray(data.getStringExtra(NativeReaderActivity.RESULT_ACTIONS) == null
        ? "[]"
        : data.getStringExtra(NativeReaderActivity.RESULT_ACTIONS)));
    } catch (JSONException error) {
      result.put("actions", new JSONArray());
    }
    JSObject settings = new JSObject();
    settings.put("fontSizeSp", data.getFloatExtra(NativeReaderActivity.RESULT_FONT_SIZE, 20f));
    settings.put("lineSpacingMultiplier", data.getFloatExtra(NativeReaderActivity.RESULT_LINE_SPACING, 1.7f));
    settings.put("paragraphSpacingDp", data.getFloatExtra(NativeReaderActivity.RESULT_PARAGRAPH_SPACING, 12f));
    settings.put("horizontalPaddingDp", data.getFloatExtra(NativeReaderActivity.RESULT_HORIZONTAL_PADDING, 24f));
    settings.put("verticalPaddingDp", data.getFloatExtra(NativeReaderActivity.RESULT_VERTICAL_PADDING, 28f));
    settings.put("textBold", data.getBooleanExtra(NativeReaderActivity.RESULT_TEXT_BOLD, false));
    settings.put("pageMode", data.getStringExtra(NativeReaderActivity.RESULT_PAGE_MODE));
    settings.put("theme", data.getStringExtra(NativeReaderActivity.RESULT_THEME));
    result.put("settings", settings);
    call.resolve(result);
  }
}
