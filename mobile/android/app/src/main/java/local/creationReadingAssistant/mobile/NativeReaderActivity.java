/* SPDX-License-Identifier: GPL-3.0-only */
package local.creationReadingAssistant.mobile;

import android.app.Activity;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import local.creationReadingAssistant.reader.legado.io.TextBookLoader;
import local.creationReadingAssistant.reader.legado.epub.EpubDocumentLoader;
import local.creationReadingAssistant.reader.legado.epub.EpubReaderDocument;
import local.creationReadingAssistant.reader.legado.model.NativeReaderSettings;
import local.creationReadingAssistant.reader.legado.model.ReaderLocator;
import local.creationReadingAssistant.reader.legado.model.ReaderPageMode;
import local.creationReadingAssistant.reader.legado.model.ReaderTheme;
import local.creationReadingAssistant.reader.legado.view.LegadoTextReaderView;
import local.creationReadingAssistant.reader.legado.view.LegadoEpubReaderView;
import local.creationReadingAssistant.reader.legado.text.TextChapter;
import local.creationReadingAssistant.reader.legado.text.TextChapterDetector;

public class NativeReaderActivity extends AppCompatActivity {
  private static final String PERFORMANCE_TAG = "NativeReaderPerf";
  private static final long READER_IDLE_TIMEOUT_MS = 90_000L;
  public static final String EXTRA_BOOK_ID = "native_reader_book_id";
  public static final String EXTRA_TITLE = "native_reader_title";
  public static final String EXTRA_AUTHOR = "native_reader_author";
  public static final String EXTRA_FILE_URI = "native_reader_file_uri";
  public static final String EXTRA_FORMAT = "native_reader_format";
  public static final String EXTRA_START_OFFSET = "native_reader_start_offset";
  public static final String EXTRA_START_PAGE_INDEX = "native_reader_start_page_index";
  public static final String EXTRA_START_CHAPTER_INDEX = "native_reader_start_chapter_index";
  public static final String EXTRA_START_EPUB_HREF = "native_reader_start_epub_href";
  public static final String EXTRA_FONT_SIZE = "native_reader_font_size";
  public static final String EXTRA_LINE_SPACING = "native_reader_line_spacing";
  public static final String EXTRA_PARAGRAPH_SPACING = "native_reader_paragraph_spacing";
  public static final String EXTRA_HORIZONTAL_PADDING = "native_reader_horizontal_padding";
  public static final String EXTRA_VERTICAL_PADDING = "native_reader_vertical_padding";
  public static final String EXTRA_TEXT_BOLD = "native_reader_text_bold";
  public static final String EXTRA_PAGE_MODE = "native_reader_page_mode";
  public static final String EXTRA_THEME = "native_reader_theme";

  public static final String RESULT_BOOK_ID = "native_reader_result_book_id";
  public static final String RESULT_CHAR_OFFSET = "native_reader_result_char_offset";
  public static final String RESULT_PAGE_INDEX = "native_reader_result_page_index";
  public static final String RESULT_PROGRESS = "native_reader_result_progress";
  public static final String RESULT_ACTIVE_DURATION = "native_reader_result_active_duration";
  public static final String RESULT_CHAPTER_TITLE = "native_reader_result_chapter_title";
  public static final String RESULT_CHAPTER_INDEX = "native_reader_result_chapter_index";
  public static final String RESULT_EPUB_HREF = "native_reader_result_epub_href";
  public static final String RESULT_FONT_SIZE = "native_reader_result_font_size";
  public static final String RESULT_LINE_SPACING = "native_reader_result_line_spacing";
  public static final String RESULT_PARAGRAPH_SPACING = "native_reader_result_paragraph_spacing";
  public static final String RESULT_HORIZONTAL_PADDING = "native_reader_result_horizontal_padding";
  public static final String RESULT_VERTICAL_PADDING = "native_reader_result_vertical_padding";
  public static final String RESULT_TEXT_BOLD = "native_reader_result_text_bold";
  public static final String RESULT_PAGE_MODE = "native_reader_result_page_mode";
  public static final String RESULT_THEME = "native_reader_result_theme";
  public static final String RESULT_ACTIONS = "native_reader_result_actions";
  public static final String RESULT_SESSION_ID = "native_reader_result_session_id";

  private final ExecutorService loaderExecutor = Executors.newSingleThreadExecutor();
  private final AtomicInteger searchGeneration = new AtomicInteger();
  private final Handler checkpointHandler = new Handler(Looper.getMainLooper());
  private final String nativeSessionId = UUID.randomUUID().toString();
  private final Runnable checkpointWriter = this::persistCheckpoint;
  private LegadoTextReaderView textReaderView;
  private LegadoEpubReaderView epubReaderView;
  private View readerSurface;
  private LinearLayout topBar;
  private LinearLayout bottomBar;
  private TextView progressText;
  private TextView readerHeading;
  private FrameLayout root;
  private View settingsPage;
  private NativeReaderSettings currentSettings;
  private long openedAt;
  private long activeDurationMs;
  private long foregroundActiveSince;
  private long lastInteractionAt;
  private String bookId = "";
  private String title = "";
  private String author = "";
  private final JSONArray pendingActions = new JSONArray();
  private ReaderLocator latestLocator = new ReaderLocator();
  private EpubReaderDocument epubDocument;
  private String loadedText = "";
  private List<TextChapter> textChapters = java.util.Collections.emptyList();
  private String currentChapterTitle;
  private boolean epubMode;
  private boolean resultSent = false;

  private static final class SearchHit {
    final String label;
    final String snippet;
    final int chapterIndex;
    final int charOffset;

    SearchHit(String label, String snippet, int chapterIndex, int charOffset) {
      this.label = label;
      this.snippet = snippet;
      this.chapterIndex = chapterIndex;
      this.charOffset = charOffset;
    }
  }

  private static final class TocEntry {
    final String title;
    final int level;
    final int chapterIndex;
    final int charOffset;

    TocEntry(String title, int level, int chapterIndex, int charOffset) {
      this.title = title;
      this.level = level;
      this.chapterIndex = chapterIndex;
      this.charOffset = charOffset;
    }
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    openedAt = SystemClock.elapsedRealtime();
    lastInteractionAt = openedAt;
    bookId = valueOrEmpty(getIntent().getStringExtra(EXTRA_BOOK_ID));
    title = valueOrEmpty(getIntent().getStringExtra(EXTRA_TITLE));
    author = valueOrEmpty(getIntent().getStringExtra(EXTRA_AUTHOR));
    epubMode = "epub".equalsIgnoreCase(valueOrEmpty(getIntent().getStringExtra(EXTRA_FORMAT)));
    currentSettings = buildSettings();
    configureSystemBars();
    buildReaderUi();
    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        if (settingsPage != null) {
          closeSecondaryPage();
        } else if (controlsVisible()) {
          setControlsVisible(false);
        } else {
          finishWithResult();
        }
      }
    });
    loadDocument();
  }

  private void buildReaderUi() {
    root = new FrameLayout(this);
    root.setBackgroundColor(themeBackground(currentSettings.getTheme()));
    if (epubMode) {
      epubReaderView = new LegadoEpubReaderView(this);
      epubReaderView.setMenuListener(() -> {
        markReaderInteraction();
        setControlsVisible(!controlsVisible());
      });
      epubReaderView.setLocationListener(locator -> onReaderLocationChanged(locator));
      epubReaderView.setSelectionListener((text, locator) -> showSelectionActions(text, locator));
      readerSurface = epubReaderView;
    } else {
      textReaderView = new LegadoTextReaderView(this);
      textReaderView.setMenuListener(() -> {
        markReaderInteraction();
        setControlsVisible(!controlsVisible());
      });
      textReaderView.setLocationListener(locator -> onReaderLocationChanged(locator));
      textReaderView.setSelectionListener((text, locator) -> showSelectionActions(text, locator));
      readerSurface = textReaderView;
    }
    root.addView(readerSurface, new FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    ));

    topBar = new LinearLayout(this);
    topBar.setOrientation(LinearLayout.HORIZONTAL);
    topBar.setGravity(Gravity.CENTER_VERTICAL);
    topBar.setPadding(dp(10), dp(8), dp(10), dp(8));
    topBar.setBackgroundColor(Color.argb(242, 246, 236, 217));
    Button back = menuButton("返回");
    back.setOnClickListener(view -> finishWithResult());
    readerHeading = new TextView(this);
    readerHeading.setText(title);
    readerHeading.setTextColor(Color.rgb(43, 35, 29));
    readerHeading.setTextSize(17f);
    readerHeading.setSingleLine(true);
    readerHeading.setPadding(dp(10), 0, dp(10), 0);
    Button search = menuButton("搜索");
    search.setOnClickListener(view -> showSearchPage());
    topBar.addView(back, new LinearLayout.LayoutParams(dp(72), dp(44)));
    topBar.addView(readerHeading, new LinearLayout.LayoutParams(0, dp(44), 1f));
    topBar.addView(search, new LinearLayout.LayoutParams(dp(64), dp(44)));
    FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT,
      Gravity.TOP
    );
    root.addView(topBar, topParams);

    bottomBar = new LinearLayout(this);
    bottomBar.setOrientation(LinearLayout.HORIZONTAL);
    bottomBar.setGravity(Gravity.CENTER);
    bottomBar.setPadding(dp(12), dp(10), dp(12), dp(14));
    bottomBar.setBackgroundColor(Color.argb(244, 246, 236, 217));
    Button previous = menuButton("上一页");
    previous.setOnClickListener(view -> goPrevious());
    progressText = new TextView(this);
    progressText.setText("0.0%");
    progressText.setGravity(Gravity.CENTER);
    progressText.setTextColor(Color.rgb(88, 72, 59));
    Button next = menuButton("下一页");
    next.setOnClickListener(view -> goNext());
    Button toc = menuButton("目录");
    toc.setVisibility(View.VISIBLE);
    toc.setOnClickListener(view -> showTocPage());
    Button settings = menuButton("设置");
    settings.setOnClickListener(view -> showSettingsPage());
    Button bookmark = menuButton("书签");
    bookmark.setOnClickListener(view -> recordReaderAction("bookmark", null));
    Button inspiration = menuButton("灵感");
    inspiration.setOnClickListener(view -> recordReaderAction("inspiration", null));
    bottomBar.addView(previous, new LinearLayout.LayoutParams(0, dp(48), 1f));
    bottomBar.addView(toc, new LinearLayout.LayoutParams(0, dp(48), 1f));
    bottomBar.addView(progressText, new LinearLayout.LayoutParams(0, dp(48), 1f));
    bottomBar.addView(bookmark, new LinearLayout.LayoutParams(0, dp(48), 1f));
    bottomBar.addView(inspiration, new LinearLayout.LayoutParams(0, dp(48), 1f));
    bottomBar.addView(settings, new LinearLayout.LayoutParams(0, dp(48), 1f));
    bottomBar.addView(next, new LinearLayout.LayoutParams(0, dp(48), 1f));
    FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT,
      Gravity.BOTTOM
    );
    root.addView(bottomBar, bottomParams);

    setContentView(root);
    applyThemeColors();
    setControlsVisible(false);
  }

  private void onReaderLocationChanged(ReaderLocator locator) {
      markReaderInteraction();
      latestLocator = normalizeReaderLocator(locator);
      if (epubReaderView != null) {
        currentChapterTitle = epubReaderView.currentChapterTitle();
      } else {
        TextChapter chapter = textChapterAt(latestLocator.getCharOffset());
        currentChapterTitle = chapter == null ? null : chapter.getTitle();
      }
      if (readerHeading != null) readerHeading.setText(currentChapterTitle == null || currentChapterTitle.isBlank() ? title : currentChapterTitle);
      if (progressText != null) {
        progressText.setText(String.format(Locale.getDefault(), "%.1f%%", latestLocator.getProgressPercent()));
      }
      scheduleCheckpoint();
  }

  private void loadDocument() {
    long loadStartedAt = SystemClock.elapsedRealtime();
    String fileUri = valueOrEmpty(getIntent().getStringExtra(EXTRA_FILE_URI));
    long startOffset = getIntent().getLongExtra(EXTRA_START_OFFSET, 0L);
    int startPageIndex = getIntent().getIntExtra(EXTRA_START_PAGE_INDEX, 0);
    showLoading("正在准备原生分页…");
    loaderExecutor.execute(() -> {
      try {
        String format = valueOrEmpty(getIntent().getStringExtra(EXTRA_FORMAT));
        if ("epub".equalsIgnoreCase(format)) {
          EpubReaderDocument loadedDocument = EpubDocumentLoader.load(this, fileUri);
          String startHref = valueOrEmpty(getIntent().getStringExtra(EXTRA_START_EPUB_HREF));
          Integer hrefChapterIndex = loadedDocument.chapterIndexForHref(startHref);
          int startChapterIndex = hrefChapterIndex == null
            ? getIntent().getIntExtra(EXTRA_START_CHAPTER_INDEX, 0)
            : hrefChapterIndex;
          startChapterIndex = Math.max(0, Math.min(startChapterIndex, loadedDocument.getChapters().size() - 1));
          loadedDocument.chapterContent(startChapterIndex);
          Log.i(PERFORMANCE_TAG, "load-ready format=epub elapsedMs=" + (SystemClock.elapsedRealtime() - loadStartedAt));
          if (isFinishing() || isDestroyed()) {
            loadedDocument.close();
            return;
          }
          final int restoredChapterIndex = startChapterIndex;
          runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
              loadedDocument.close();
              return;
            }
            epubDocument = loadedDocument;
            hideStatusOverlay();
            epubReaderView.updateSettings(currentSettings);
            epubReaderView.setDocument(
              epubDocument,
              restoredChapterIndex,
              (int) Math.min(Integer.MAX_VALUE, Math.max(0L, startOffset)),
              Math.max(0, startPageIndex)
            );
          });
        } else {
          String content = TextBookLoader.load(this, fileUri);
          Log.i(PERFORMANCE_TAG, "load-ready format=text elapsedMs=" + (SystemClock.elapsedRealtime() - loadStartedAt));
          loadedText = content;
          textChapters = TextChapterDetector.INSTANCE.detect(content);
          runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            hideStatusOverlay();
            textReaderView.updateSettings(currentSettings);
            textReaderView.setDocument(
              title,
              content,
              (int) Math.min(Integer.MAX_VALUE, Math.max(0L, startOffset)),
              Math.max(0, startPageIndex)
            );
          });
        }
      } catch (Throwable error) {
        Log.e(PERFORMANCE_TAG, "load-failed elapsedMs=" + (SystemClock.elapsedRealtime() - loadStartedAt), error);
        runOnUiThread(() -> {
          if (!isFinishing() && !isDestroyed()) {
            showError(error.getMessage() == null ? "本地正文读取失败" : error.getMessage());
          }
        });
      }
    });
  }

  private void goPrevious() {
    markReaderInteraction();
    if (epubReaderView != null) epubReaderView.goPrevious();
    else if (textReaderView != null) textReaderView.goPrevious();
  }

  private void goNext() {
    markReaderInteraction();
    if (epubReaderView != null) epubReaderView.goNext();
    else if (textReaderView != null) textReaderView.goNext();
  }

  private ReaderLocator currentLocator() {
    if (epubReaderView != null) return normalizeReaderLocator(epubReaderView.currentLocator());
    if (textReaderView != null) return normalizeReaderLocator(textReaderView.currentLocator());
    return normalizeReaderLocator(latestLocator);
  }

  private ReaderLocator normalizeReaderLocator(ReaderLocator locator) {
    if (epubMode) return locator;
    TextChapter chapter = textChapterAt(locator.getCharOffset());
    int chapterIndex = chapter == null ? 0 : chapter.getIndex();
    return new ReaderLocator(
      chapterIndex,
      locator.getCharOffset(),
      locator.getPageIndex(),
      locator.getProgressPercent(),
      null,
      null
    );
  }

  @Nullable
  private TextChapter textChapterAt(long rawOffset) {
    if (textChapters == null || textChapters.isEmpty()) return null;
    int offset = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, rawOffset));
    int low = 0;
    int high = textChapters.size() - 1;
    TextChapter candidate = textChapters.get(0);
    while (low <= high) {
      int middle = (low + high) >>> 1;
      TextChapter chapter = textChapters.get(middle);
      if (chapter.getStartOffset() <= offset) {
        candidate = chapter;
        low = middle + 1;
      } else {
        high = middle - 1;
      }
    }
    return candidate;
  }

  private void recordReaderAction(String type, @Nullable String excerpt) {
    recordReaderAction(type, excerpt, null);
  }

  private void recordReaderAction(String type, @Nullable String excerpt, @Nullable ReaderLocator locatorOverride) {
    markReaderInteraction();
    ReaderLocator locator = normalizeReaderLocator(locatorOverride == null ? currentLocator() : locatorOverride);
    JSONObject action = new JSONObject();
    try {
      action.put("type", type);
      action.put("bookId", bookId);
      action.put("bookTitle", title);
      action.put("bookAuthor", author);
      action.put("format", epubMode ? "epub" : "txt");
      action.put("chapterTitle", currentChapterTitle == null ? JSONObject.NULL : currentChapterTitle);
      action.put("chapterIndex", locator.getChapterIndex());
      action.put("epubHref", locator.getEpubHref() == null ? JSONObject.NULL : locator.getEpubHref());
      action.put("charOffset", locator.getCharOffset());
      action.put("pageIndex", locator.getPageIndex());
      action.put("progressPercent", locator.getProgressPercent());
      action.put("excerpt", excerpt == null || excerpt.isBlank() ? JSONObject.NULL : excerpt.trim());
      JSONObject persistedAction = NativeReaderActionJournal.append(this, action);
      pendingActions.put(persistedAction);
      String message = "bookmark".equals(type)
        ? "已记录当前位置书签"
        : "note".equals(type) ? "已记录阅读笔记" : "已记录为灵感";
      Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    } catch (JSONException error) {
      Toast.makeText(this, "记录失败，请稍后重试", Toast.LENGTH_SHORT).show();
    }
  }

  private void showSelectionActions(String selectedText, ReaderLocator selectionLocator) {
    if (selectedText == null || selectedText.isBlank()) return;
    markReaderInteraction();
    closeSecondaryPage();
    setControlsVisible(false);

    LinearLayout page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setPadding(dp(20), dp(18), dp(20), dp(28));
    page.setBackgroundColor(themeBackground(currentSettings.getTheme()));

    LinearLayout header = new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    Button back = menuButton("← 返回");
    back.setOnClickListener(view -> closeSecondaryPage());
    TextView heading = settingsLabel("选中文字");
    heading.setTextSize(22f);
    header.addView(back, new LinearLayout.LayoutParams(dp(88), dp(48)));
    header.addView(heading, new LinearLayout.LayoutParams(0, dp(48), 1f));
    page.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

    EditText excerptEditor = new EditText(this);
    excerptEditor.setText(selectedText);
    excerptEditor.setTextColor(themeText(currentSettings.getTheme()));
    excerptEditor.setTextSize(17f);
    excerptEditor.setGravity(Gravity.TOP | Gravity.START);
    excerptEditor.setPadding(dp(14), dp(14), dp(14), dp(14));
    excerptEditor.setBackgroundColor(Color.TRANSPARENT);
    page.addView(excerptEditor, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

    LinearLayout actions = new LinearLayout(this);
    actions.setGravity(Gravity.CENTER);
    Button copy = menuButton("复制");
    copy.setOnClickListener(view -> {
      ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
      clipboard.setPrimaryClip(ClipData.newPlainText("阅读摘录", excerptEditor.getText().toString()));
      Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
    });
    Button note = menuButton("记笔记");
    note.setOnClickListener(view -> {
      recordReaderAction("note", excerptEditor.getText().toString(), selectionLocator);
      closeSecondaryPage();
    });
    Button inspiration = menuButton("记为灵感");
    inspiration.setOnClickListener(view -> {
      recordReaderAction("inspiration", excerptEditor.getText().toString(), selectionLocator);
      closeSecondaryPage();
    });
    actions.addView(copy, new LinearLayout.LayoutParams(0, dp(52), 1f));
    actions.addView(note, new LinearLayout.LayoutParams(0, dp(52), 1f));
    actions.addView(inspiration, new LinearLayout.LayoutParams(0, dp(52), 1f));
    page.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));

    settingsPage = page;
    root.addView(page, matchOverlayParams());
  }

  private void showSearchPage() {
    if ((!epubMode && loadedText.isBlank()) || (epubMode && epubDocument == null)) return;
    markReaderInteraction();
    closeSecondaryPage();
    setControlsVisible(false);

    LinearLayout page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setPadding(dp(18), dp(16), dp(18), dp(24));
    page.setBackgroundColor(themeBackground(currentSettings.getTheme()));

    LinearLayout header = new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    Button back = menuButton("← 返回");
    back.setOnClickListener(view -> closeSecondaryPage());
    TextView heading = settingsLabel("书内搜索");
    heading.setTextSize(22f);
    header.addView(back, new LinearLayout.LayoutParams(dp(88), dp(48)));
    header.addView(heading, new LinearLayout.LayoutParams(0, dp(48), 1f));
    page.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

    LinearLayout searchRow = new LinearLayout(this);
    EditText queryInput = new EditText(this);
    queryInput.setSingleLine(true);
    queryInput.setHint("输入正文关键词");
    queryInput.setTextColor(themeText(currentSettings.getTheme()));
    queryInput.setHintTextColor(themeSecondary(currentSettings.getTheme()));
    Button submit = menuButton("查找");
    searchRow.addView(queryInput, new LinearLayout.LayoutParams(0, dp(52), 1f));
    searchRow.addView(submit, new LinearLayout.LayoutParams(dp(72), dp(52)));
    page.addView(searchRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

    ScrollView resultScroll = new ScrollView(this);
    LinearLayout resultList = new LinearLayout(this);
    resultList.setOrientation(LinearLayout.VERTICAL);
    resultScroll.addView(resultList, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    page.addView(resultScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

    submit.setOnClickListener(view -> populateSearchResults(resultList, queryInput.getText().toString()));
    settingsPage = page;
    root.addView(page, matchOverlayParams());
  }

  private void populateSearchResults(LinearLayout resultList, String rawQuery) {
    int generation = searchGeneration.incrementAndGet();
    resultList.removeAllViews();
    String query = rawQuery == null ? "" : rawQuery.trim();
    if (query.isBlank()) {
      TextView hint = settingsLabel("请输入要搜索的内容。");
      resultList.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
      return;
    }
    TextView loading = settingsLabel("正在搜索…");
    resultList.addView(loading, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
    loaderExecutor.execute(() -> {
      List<SearchHit> hits = buildSearchHits(query, generation);
      runOnUiThread(() -> {
        if (generation != searchGeneration.get() || isFinishing() || isDestroyed() || !resultList.isAttachedToWindow()) return;
        resultList.removeAllViews();
        for (SearchHit hit : hits) {
          addSearchResultButton(resultList, hit.label, hit.snippet, () -> {
            if (epubMode && epubReaderView != null) {
              epubReaderView.goToChapter(hit.chapterIndex, hit.charOffset);
            } else if (textReaderView != null) {
              textReaderView.goToOffset(hit.charOffset);
            }
            closeSecondaryPage();
          });
        }
        if (hits.isEmpty()) {
          TextView empty = settingsLabel("没有找到相关内容。");
          resultList.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        }
      });
    });
  }

  private List<SearchHit> buildSearchHits(String query, int generation) {
    List<SearchHit> hits = new ArrayList<>();
    Pattern searchPattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    if (epubMode && epubDocument != null) {
      for (local.creationReadingAssistant.reader.legado.epub.EpubChapter chapter : epubDocument.getChapters()) {
        if (generation != searchGeneration.get() || Thread.currentThread().isInterrupted()) break;
        String chapterText;
        try {
          chapterText = epubDocument.chapterText(chapter.getIndex());
        } catch (Throwable ignored) {
          continue;
        }
        Matcher matcher = searchPattern.matcher(chapterText);
        if (!matcher.find()) continue;
        int localOffset = matcher.start();
        hits.add(new SearchHit(
          chapter.getTitle(),
          searchSnippet(chapterText, localOffset, query.length()),
          chapter.getIndex(),
          localOffset
        ));
        if (hits.size() >= 50) break;
      }
    } else {
      Matcher matcher = searchPattern.matcher(loadedText);
      while (hits.size() < 50 && matcher.find()) {
        int offset = matcher.start();
        TextChapter chapter = textChapterAt(offset);
        hits.add(new SearchHit(
          chapter == null ? "正文位置 " + (offset + 1) : chapter.getTitle(),
          searchSnippet(loadedText, offset, query.length()),
          chapter == null ? 0 : chapter.getIndex(),
          offset
        ));
      }
    }
    return hits;
  }

  private void addSearchResultButton(LinearLayout parent, String label, String snippet, Runnable action) {
    Button result = menuButton(label + "\n" + snippet);
    result.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    result.setTextSize(15f);
    result.setPadding(dp(12), dp(8), dp(12), dp(8));
    result.setOnClickListener(view -> action.run());
    parent.addView(result, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));
  }

  private String searchSnippet(String content, int offset, int queryLength) {
    int start = Math.max(0, offset - 24);
    int end = Math.min(content.length(), offset + Math.max(1, queryLength) + 40);
    return content.substring(start, end).replace('\n', ' ').trim();
  }

  private void updateReaderSettings() {
    if (epubReaderView != null) epubReaderView.updateSettings(currentSettings);
    if (textReaderView != null) textReaderView.updateSettings(currentSettings);
    scheduleCheckpoint();
  }

  private NativeReaderSettings buildSettings() {
    float fontSize = (float) getIntent().getDoubleExtra(EXTRA_FONT_SIZE, 20.0);
    float lineSpacing = (float) getIntent().getDoubleExtra(EXTRA_LINE_SPACING, 1.7);
    float paragraphSpacing = (float) getIntent().getDoubleExtra(EXTRA_PARAGRAPH_SPACING, 12.0);
    float horizontalPadding = (float) getIntent().getDoubleExtra(EXTRA_HORIZONTAL_PADDING, 24.0);
    float verticalPadding = (float) getIntent().getDoubleExtra(EXTRA_VERTICAL_PADDING, 28.0);
    boolean bold = getIntent().getBooleanExtra(EXTRA_TEXT_BOLD, false);
    ReaderPageMode pageMode = parsePageMode(getIntent().getStringExtra(EXTRA_PAGE_MODE));
    ReaderTheme theme = parseTheme(getIntent().getStringExtra(EXTRA_THEME));
    return new NativeReaderSettings(
      fontSize,
      lineSpacing,
      paragraphSpacing,
      horizontalPadding,
      verticalPadding,
      bold,
      pageMode,
      theme
    );
  }

  private void showSettingsPage() {
    markReaderInteraction();
    closeSecondaryPage();
    setControlsVisible(false);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(themeBackground(currentSettings.getTheme()));

    LinearLayout page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setPadding(dp(20), dp(18), dp(20), dp(28));
    page.setBackgroundColor(themeBackground(currentSettings.getTheme()));

    LinearLayout header = new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    Button back = menuButton("← 返回");
    back.setOnClickListener(view -> closeSecondaryPage());
    TextView heading = new TextView(this);
    heading.setText("阅读设置");
    heading.setTextSize(22f);
    heading.setTextColor(themeText(currentSettings.getTheme()));
    heading.setGravity(Gravity.CENTER_VERTICAL);
    header.addView(back, new LinearLayout.LayoutParams(dp(88), dp(48)));
    header.addView(heading, new LinearLayout.LayoutParams(0, dp(48), 1f));
    page.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

    page.addView(settingStepper(
      "字号",
      String.format(Locale.getDefault(), "%.0f sp", currentSettings.getFontSizeSp()),
      view -> replaceSettings(Math.max(14f, currentSettings.getFontSizeSp() - 1f), null, null, null, null, null, null),
      view -> replaceSettings(Math.min(34f, currentSettings.getFontSizeSp() + 1f), null, null, null, null, null, null)
    ));
    page.addView(settingStepper(
      "行距",
      String.format(Locale.getDefault(), "%.1f", currentSettings.getLineSpacingMultiplier()),
      view -> replaceSettings(null, Math.max(1.2f, currentSettings.getLineSpacingMultiplier() - 0.1f), null, null, null, null, null),
      view -> replaceSettings(null, Math.min(2.4f, currentSettings.getLineSpacingMultiplier() + 0.1f), null, null, null, null, null)
    ));
    page.addView(settingStepper(
      "页边距",
      String.format(Locale.getDefault(), "%.0f dp", currentSettings.getHorizontalPaddingDp()),
      view -> replaceSettings(null, null, null, Math.max(12f, currentSettings.getHorizontalPaddingDp() - 2f), null, null, null),
      view -> replaceSettings(null, null, null, Math.min(48f, currentSettings.getHorizontalPaddingDp() + 2f), null, null, null)
    ));

    LinearLayout boldRow = settingsRow("字重");
    Button bold = menuButton(currentSettings.getTextBold() ? "已加粗" : "常规");
    bold.setOnClickListener(view -> replaceSettings(null, null, null, null, null, !currentSettings.getTextBold(), null));
    boldRow.addView(bold, new LinearLayout.LayoutParams(dp(112), dp(44)));
    page.addView(boldRow);

    TextView themeTitle = settingsLabel("阅读背景");
    themeTitle.setPadding(0, dp(22), 0, dp(10));
    page.addView(themeTitle);
    LinearLayout themeRow = new LinearLayout(this);
    themeRow.setOrientation(LinearLayout.HORIZONTAL);
    themeRow.setGravity(Gravity.CENTER_VERTICAL);
    addThemeButton(themeRow, "白纸", ReaderTheme.WHITE);
    addThemeButton(themeRow, "暖纸", ReaderTheme.WARM);
    addThemeButton(themeRow, "护眼", ReaderTheme.GREEN);
    addThemeButton(themeRow, "夜间", ReaderTheme.NIGHT);
    page.addView(themeRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

    TextView modeHint = settingsLabel("当前使用原生左右分页。上下滚动模式仍由兼容内核承载，切换后下次打开生效。");
    modeHint.setTextSize(14f);
    modeHint.setPadding(0, dp(22), 0, 0);
    page.addView(modeHint);

    scroll.addView(page, new ScrollView.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT
    ));
    settingsPage = scroll;
    root.addView(scroll, matchOverlayParams());
  }

  private LinearLayout settingStepper(
    String label,
    String value,
    View.OnClickListener minusListener,
    View.OnClickListener plusListener
  ) {
    LinearLayout row = settingsRow(label);
    Button minus = menuButton("−");
    minus.setTextSize(22f);
    minus.setOnClickListener(minusListener);
    TextView current = settingsLabel(value);
    current.setGravity(Gravity.CENTER);
    Button plus = menuButton("+");
    plus.setTextSize(22f);
    plus.setOnClickListener(plusListener);
    row.addView(minus, new LinearLayout.LayoutParams(dp(48), dp(44)));
    row.addView(current, new LinearLayout.LayoutParams(dp(92), dp(44)));
    row.addView(plus, new LinearLayout.LayoutParams(dp(48), dp(44)));
    return row;
  }

  private LinearLayout settingsRow(String label) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0, dp(9), 0, dp(9));
    TextView titleView = settingsLabel(label);
    titleView.setTextSize(17f);
    row.addView(titleView, new LinearLayout.LayoutParams(0, dp(48), 1f));
    return row;
  }

  private TextView settingsLabel(String text) {
    TextView label = new TextView(this);
    label.setText(text);
    label.setTextColor(themeText(currentSettings.getTheme()));
    label.setTextSize(16f);
    label.setGravity(Gravity.CENTER_VERTICAL);
    return label;
  }

  private void addThemeButton(LinearLayout row, String label, ReaderTheme theme) {
    Button button = menuButton((currentSettings.getTheme() == theme ? "✓ " : "") + label);
    button.setOnClickListener(view -> replaceSettings(null, null, null, null, null, null, theme));
    row.addView(button, new LinearLayout.LayoutParams(0, dp(48), 1f));
  }

  private void replaceSettings(
    Float fontSize,
    Float lineSpacing,
    Float paragraphSpacing,
    Float horizontalPadding,
    Float verticalPadding,
    Boolean bold,
    ReaderTheme theme
  ) {
    currentSettings = new NativeReaderSettings(
      fontSize == null ? currentSettings.getFontSizeSp() : fontSize,
      lineSpacing == null ? currentSettings.getLineSpacingMultiplier() : lineSpacing,
      paragraphSpacing == null ? currentSettings.getParagraphSpacingDp() : paragraphSpacing,
      horizontalPadding == null ? currentSettings.getHorizontalPaddingDp() : horizontalPadding,
      verticalPadding == null ? currentSettings.getVerticalPaddingDp() : verticalPadding,
      bold == null ? currentSettings.getTextBold() : bold,
      ReaderPageMode.SLIDE,
      theme == null ? currentSettings.getTheme() : theme
    );
    updateReaderSettings();
    applyThemeColors();
    showSettingsPage();
  }

  private void closeSecondaryPage() {
    searchGeneration.incrementAndGet();
    if (settingsPage != null) {
      root.removeView(settingsPage);
      settingsPage = null;
    }
  }

  private void showTocPage() {
    markReaderInteraction();
    if (epubMode && (epubDocument == null || epubReaderView == null)) return;
    if (!epubMode && (loadedText.isBlank() || textReaderView == null)) return;
    closeSecondaryPage();
    setControlsVisible(false);

    LinearLayout page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setPadding(dp(18), dp(16), dp(18), dp(30));
    page.setBackgroundColor(themeBackground(currentSettings.getTheme()));

    LinearLayout header = new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    Button back = menuButton("← 返回");
    back.setOnClickListener(view -> closeSecondaryPage());
    TextView heading = settingsLabel("目录");
    heading.setTextSize(22f);
    header.addView(back, new LinearLayout.LayoutParams(dp(88), dp(48)));
    header.addView(heading, new LinearLayout.LayoutParams(0, dp(48), 1f));
    page.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

    int activeChapter = currentLocator().getChapterIndex();
    List<TocEntry> entries = new ArrayList<>();
    if (epubMode) {
      for (local.creationReadingAssistant.reader.legado.epub.EpubChapter chapter : epubDocument.getChapters()) {
        entries.add(new TocEntry(chapter.getTitle(), chapter.getLevel(), chapter.getIndex(), 0));
      }
    } else {
      for (TextChapter chapter : textChapters) {
        entries.add(new TocEntry(chapter.getTitle(), 1, chapter.getIndex(), chapter.getStartOffset()));
      }
    }

    ListView chapterList = new ListView(this);
    chapterList.setDividerHeight(0);
    chapterList.setAdapter(new BaseAdapter() {
      @Override
      public int getCount() {
        return entries.size();
      }

      @Override
      public TocEntry getItem(int position) {
        return entries.get(position);
      }

      @Override
      public long getItemId(int position) {
        return getItem(position).chapterIndex;
      }

      @Override
      public View getView(int position, View convertView, ViewGroup parent) {
        TextView chapterView = convertView instanceof TextView ? (TextView) convertView : new TextView(NativeReaderActivity.this);
        TocEntry entry = getItem(position);
        boolean active = entry.chapterIndex == activeChapter;
        chapterView.setText((active ? "● " : "") + entry.title);
        chapterView.setTextColor(themeText(currentSettings.getTheme()));
        chapterView.setTextSize(active ? 17f : 16f);
        chapterView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        chapterView.setSingleLine(true);
        chapterView.setPadding(dp(12 + Math.min(4, Math.max(0, entry.level - 1)) * 16), 0, dp(12), 0);
        chapterView.setLayoutParams(new android.widget.AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        return chapterView;
      }
    });
    chapterList.setOnItemClickListener((parent, view, position, id) -> {
      TocEntry entry = entries.get(position);
      if (epubMode && epubReaderView != null) {
        epubReaderView.goToChapter(entry.chapterIndex, 0);
      } else if (textReaderView != null) {
        textReaderView.goToOffset(entry.charOffset);
      }
      closeSecondaryPage();
    });
    page.addView(chapterList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    settingsPage = page;
    root.addView(page, matchOverlayParams());
    if (activeChapter >= 0 && activeChapter < entries.size()) {
      chapterList.setSelection(Math.max(0, activeChapter - 2));
    }
  }

  private void applyThemeColors() {
    int background = themeBackground(currentSettings.getTheme());
    int text = themeText(currentSettings.getTheme());
    int secondary = themeSecondary(currentSettings.getTheme());
    root.setBackgroundColor(background);
    topBar.setBackgroundColor(background);
    bottomBar.setBackgroundColor(background);
    progressText.setTextColor(secondary);
    tintChildren(topBar, text);
    tintChildren(bottomBar, text);
    getWindow().setStatusBarColor(background);
    getWindow().setNavigationBarColor(background);
    getWindow().getDecorView().setSystemUiVisibility(
      currentSettings.getTheme() == ReaderTheme.NIGHT ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    );
  }

  private void tintChildren(ViewGroup group, int color) {
    for (int index = 0; index < group.getChildCount(); index += 1) {
      View child = group.getChildAt(index);
      if (child instanceof TextView) ((TextView) child).setTextColor(color);
    }
  }

  private int themeBackground(ReaderTheme theme) {
    if (theme == ReaderTheme.WHITE) return Color.rgb(250, 250, 248);
    if (theme == ReaderTheme.GREEN) return Color.rgb(220, 234, 215);
    if (theme == ReaderTheme.NIGHT) return Color.rgb(20, 22, 24);
    return Color.rgb(246, 236, 217);
  }

  private int themeText(ReaderTheme theme) {
    if (theme == ReaderTheme.WHITE) return Color.rgb(32, 31, 29);
    if (theme == ReaderTheme.GREEN) return Color.rgb(34, 47, 34);
    if (theme == ReaderTheme.NIGHT) return Color.rgb(207, 210, 212);
    return Color.rgb(43, 35, 29);
  }

  private int themeSecondary(ReaderTheme theme) {
    if (theme == ReaderTheme.WHITE) return Color.rgb(112, 108, 101);
    if (theme == ReaderTheme.GREEN) return Color.rgb(85, 108, 84);
    if (theme == ReaderTheme.NIGHT) return Color.rgb(132, 139, 143);
    return Color.rgb(123, 101, 81);
  }

  private void showLoading(String message) {
    LinearLayout overlay = statusOverlay(message, false);
    overlay.setTag("reader-status-overlay");
    ProgressBar progress = new ProgressBar(this);
    overlay.addView(progress, 0, new LinearLayout.LayoutParams(dp(42), dp(42)));
    root.addView(overlay, matchOverlayParams());
  }

  private void showError(String message) {
    hideStatusOverlay();
    LinearLayout overlay = statusOverlay("打开失败\n" + message, true);
    overlay.setTag("reader-status-overlay");
    Button back = menuButton("返回书架");
    back.setOnClickListener(view -> finishWithResult());
    overlay.addView(back, new LinearLayout.LayoutParams(dp(150), dp(48)));
    root.addView(overlay, matchOverlayParams());
  }

  private LinearLayout statusOverlay(String message, boolean isError) {
    LinearLayout overlay = new LinearLayout(this);
    overlay.setOrientation(LinearLayout.VERTICAL);
    overlay.setGravity(Gravity.CENTER);
    overlay.setPadding(dp(28), dp(28), dp(28), dp(28));
    overlay.setBackgroundColor(themeBackground(currentSettings.getTheme()));
    TextView label = new TextView(this);
    label.setText(message);
    label.setTextSize(isError ? 17f : 15f);
    label.setTextColor(isError ? Color.rgb(190, 70, 56) : themeSecondary(currentSettings.getTheme()));
    label.setGravity(Gravity.CENTER);
    label.setPadding(0, dp(18), 0, dp(18));
    overlay.addView(label, new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.WRAP_CONTENT,
      ViewGroup.LayoutParams.WRAP_CONTENT
    ));
    return overlay;
  }

  private FrameLayout.LayoutParams matchOverlayParams() {
    return new FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
    );
  }

  private void hideStatusOverlay() {
    View overlay = root.findViewWithTag("reader-status-overlay");
    if (overlay != null) root.removeView(overlay);
  }

  private Button menuButton(String text) {
    Button button = new Button(this);
    button.setText(text);
    button.setTextSize(14f);
    button.setTextColor(currentSettings == null ? Color.rgb(67, 52, 41) : themeText(currentSettings.getTheme()));
    button.setAllCaps(false);
    button.setBackgroundColor(Color.TRANSPARENT);
    button.setMinWidth(0);
    button.setMinimumWidth(0);
    button.setMinHeight(0);
    button.setMinimumHeight(0);
    return button;
  }

  private boolean controlsVisible() {
    return topBar != null && topBar.getVisibility() == View.VISIBLE;
  }

  private void setControlsVisible(boolean visible) {
    int visibility = visible ? View.VISIBLE : View.GONE;
    if (topBar != null) topBar.setVisibility(visibility);
    if (bottomBar != null) bottomBar.setVisibility(visibility);
  }

  private void finishWithResult() {
    if (resultSent) {
      finish();
      return;
    }
    resultSent = true;
    flushActiveDuration(SystemClock.elapsedRealtime());
    persistCheckpoint();
    ReaderLocator locator = currentLocator();
    Intent result = new Intent();
    result.putExtra(RESULT_BOOK_ID, bookId);
    result.putExtra(RESULT_CHAR_OFFSET, locator.getCharOffset());
    result.putExtra(RESULT_PAGE_INDEX, locator.getPageIndex());
    result.putExtra(RESULT_PROGRESS, locator.getProgressPercent());
    result.putExtra(RESULT_ACTIVE_DURATION, Math.max(0L, activeDurationMs));
    result.putExtra(RESULT_CHAPTER_TITLE, currentChapterTitle);
    result.putExtra(RESULT_CHAPTER_INDEX, locator.getChapterIndex());
    result.putExtra(RESULT_EPUB_HREF, locator.getEpubHref());
    result.putExtra(RESULT_FONT_SIZE, currentSettings.getFontSizeSp());
    result.putExtra(RESULT_LINE_SPACING, currentSettings.getLineSpacingMultiplier());
    result.putExtra(RESULT_PARAGRAPH_SPACING, currentSettings.getParagraphSpacingDp());
    result.putExtra(RESULT_HORIZONTAL_PADDING, currentSettings.getHorizontalPaddingDp());
    result.putExtra(RESULT_VERTICAL_PADDING, currentSettings.getVerticalPaddingDp());
    result.putExtra(RESULT_TEXT_BOLD, currentSettings.getTextBold());
    result.putExtra(RESULT_PAGE_MODE, currentSettings.getPageMode().name());
    result.putExtra(RESULT_THEME, currentSettings.getTheme().name());
    result.putExtra(RESULT_ACTIONS, pendingActions.toString());
    result.putExtra(RESULT_SESSION_ID, nativeSessionId);
    setResult(Activity.RESULT_OK, result);
    finish();
  }

  private void configureSystemBars() {
    getWindow().setStatusBarColor(Color.rgb(246, 236, 217));
    getWindow().setNavigationBarColor(Color.rgb(246, 236, 217));
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
  }

  private ReaderTheme parseTheme(String value) {
    try {
      return ReaderTheme.valueOf(value == null ? "WARM" : value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException error) {
      return ReaderTheme.WARM;
    }
  }

  private ReaderPageMode parsePageMode(String value) {
    try {
      return ReaderPageMode.valueOf(value == null ? "SLIDE" : value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException error) {
      return ReaderPageMode.SLIDE;
    }
  }

  private int dp(int value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value;
  }

  private void markReaderInteraction() {
    long now = SystemClock.elapsedRealtime();
    if (foregroundActiveSince > 0L && now - lastInteractionAt > READER_IDLE_TIMEOUT_MS) {
      flushActiveDuration(now);
      foregroundActiveSince = now;
    }
    lastInteractionAt = now;
  }

  private void scheduleCheckpoint() {
    checkpointHandler.removeCallbacks(checkpointWriter);
    checkpointHandler.postDelayed(checkpointWriter, 750L);
  }

  private long activeDurationSnapshot(long now) {
    long result = Math.max(0L, activeDurationMs);
    if (foregroundActiveSince > 0L) {
      long activeUntil = Math.min(now, lastInteractionAt + READER_IDLE_TIMEOUT_MS);
      result += Math.max(0L, activeUntil - foregroundActiveSince);
    }
    return result;
  }

  private void persistCheckpoint() {
    if (bookId.isBlank() || currentSettings == null) return;
    ReaderLocator locator = currentLocator();
    JSONObject checkpoint = new JSONObject();
    JSONObject locatorJson = new JSONObject();
    JSONObject settingsJson = new JSONObject();
    try {
      locatorJson.put("chapterIndex", locator.getChapterIndex());
      locatorJson.put("charOffset", locator.getCharOffset());
      locatorJson.put("pageIndex", locator.getPageIndex());
      locatorJson.put("progressPercent", locator.getProgressPercent());
      locatorJson.put("epubHref", locator.getEpubHref() == null ? JSONObject.NULL : locator.getEpubHref());
      settingsJson.put("fontSizeSp", currentSettings.getFontSizeSp());
      settingsJson.put("lineSpacingMultiplier", currentSettings.getLineSpacingMultiplier());
      settingsJson.put("paragraphSpacingDp", currentSettings.getParagraphSpacingDp());
      settingsJson.put("horizontalPaddingDp", currentSettings.getHorizontalPaddingDp());
      settingsJson.put("verticalPaddingDp", currentSettings.getVerticalPaddingDp());
      settingsJson.put("textBold", currentSettings.getTextBold());
      settingsJson.put("pageMode", currentSettings.getPageMode().name());
      settingsJson.put("theme", currentSettings.getTheme().name());
      checkpoint.put("sessionId", nativeSessionId);
      checkpoint.put("bookId", bookId);
      checkpoint.put("bookTitle", title);
      checkpoint.put("format", epubMode ? "epub" : "txt");
      checkpoint.put("chapterTitle", currentChapterTitle == null ? JSONObject.NULL : currentChapterTitle);
      checkpoint.put("activeDurationMs", activeDurationSnapshot(SystemClock.elapsedRealtime()));
      checkpoint.put("updatedAt", System.currentTimeMillis());
      checkpoint.put("locator", locatorJson);
      checkpoint.put("settings", settingsJson);
      NativeReaderCheckpointJournal.save(this, checkpoint);
    } catch (JSONException ignored) {
      // A failed checkpoint must never interrupt reading; the final activity
      // result remains the primary persistence path.
    }
  }

  private void flushActiveDuration(long now) {
    if (foregroundActiveSince <= 0L) return;
    long activeUntil = Math.min(now, lastInteractionAt + READER_IDLE_TIMEOUT_MS);
    activeDurationMs += Math.max(0L, activeUntil - foregroundActiveSince);
    foregroundActiveSince = 0L;
  }

  @Override
  protected void onResume() {
    super.onResume();
    long now = SystemClock.elapsedRealtime();
    foregroundActiveSince = now;
    lastInteractionAt = now;
  }

  @Override
  protected void onPause() {
    flushActiveDuration(SystemClock.elapsedRealtime());
    persistCheckpoint();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    checkpointHandler.removeCallbacksAndMessages(null);
    loaderExecutor.shutdownNow();
    if (epubDocument != null) {
      epubDocument.close();
      epubDocument = null;
    }
    super.onDestroy();
  }
}
