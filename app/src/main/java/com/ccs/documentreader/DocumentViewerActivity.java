package com.ccs.documentreader;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONObject;

import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Перегляд документів у WebView. Великі текстові файли читаються потоково по 1 МБ
 * з «вікном» у DOM (~{@link RichDocumentReader#STREAM_MAX_DOM_CHUNKS} МБ).
 */
public class DocumentViewerActivity extends AppCompatActivity {

    public static final String EXTRA_FILE_URI = "file_uri";
    public static final String EXTRA_FILE_NAME = "file_name";

    private static final String PREFS = "viewer_prefs";
    private static final String KEY_DARK = "dark_mode";
    private static final String KEY_ZOOM = "text_zoom";

    private MaterialToolbar toolbar;
    private WebView webView;
    private ProgressBar progressBar;
    private View searchBar;
    private EditText searchInput;
    private ImageButton btnSearchPrev, btnSearchNext, btnSearchClose;

    private RichDocumentReader reader;
    private ExecutorService executor;

    private SharedPreferences prefs;
    private boolean darkMode;
    private int textZoom = 100;

    private String fileName;
    private Uri uri;

    /** Потоковий текст */
    private volatile Utf8StreamChunkReader streamReader;
    private boolean streamingMode;
    private int streamChunkId = 0;
    private int streamFirstDomChunk = 0;
    private volatile boolean streamEofReached;
    /** Потоковий режим для .java — підсвітка по чанках (innerHTML). */
    private boolean streamJavaSyntax;

    /** EPUB: сесія + глави по одній, eviction старих вузлів у WebView. */
    private static final int MAX_EPUB_DOM_WRAPPERS = 8;
    private volatile EpubReader.Session epubSession;
    private final AtomicInteger epubNextChapter = new AtomicInteger(0);
    private int epubFirstDomWrapper = 0;
    private boolean epubMode;
    private volatile boolean epubEofReached;
    private volatile boolean epubTempDeleted;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document_viewer);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        darkMode = prefs.getBoolean(KEY_DARK, false);
        textZoom = prefs.getInt(KEY_ZOOM, 100);

        initViews();
        setupToolbar();
        setupWebView();
        setupSearch();

        reader = new RichDocumentReader(this);
        executor = Executors.newSingleThreadExecutor();

        loadDocument();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        searchBar = findViewById(R.id.searchBar);
        searchInput = findViewById(R.id.searchInput);
        btnSearchPrev = findViewById(R.id.btnSearchPrev);
        btnSearchNext = findViewById(R.id.btnSearchNext);
        btnSearchClose = findViewById(R.id.btnSearchClose);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setDefaultTextEncodingName("UTF-8");
        s.setLoadsImagesAutomatically(true);
        s.setBlockNetworkImage(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
        s.setTextZoom(textZoom);

        webView.setFindListener((active, count, done) -> {
            if (done && count == 0 && !searchInput.getText().toString().isEmpty()) {
                Toast.makeText(this, R.string.search_not_found, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int s, int co, int a) {}
            @Override public void onTextChanged(CharSequence c, int s, int b, int co) {
                String q = c.toString();
                if (q.isEmpty()) webView.clearMatches();
                else webView.findAllAsync(q);
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        btnSearchNext.setOnClickListener(v -> webView.findNext(true));
        btnSearchPrev.setOnClickListener(v -> webView.findNext(false));
        btnSearchClose.setOnClickListener(v -> closeSearch());
    }

    private void openSearch() {
        searchBar.setVisibility(View.VISIBLE);
        searchInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void closeSearch() {
        webView.clearMatches();
        searchInput.setText("");
        searchBar.setVisibility(View.GONE);
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
    }

    private void loadDocument() {
        String uriString = getIntent().getStringExtra(EXTRA_FILE_URI);
        fileName = getIntent().getStringExtra(EXTRA_FILE_NAME);
        if (fileName == null) fileName = "document";

        if (uriString == null) {
            Toast.makeText(this, R.string.loading_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        uri = Uri.parse(uriString);
        toolbar.setTitle(fileName);

        String ext = extensionOf(fileName);
        if ("epub".equalsIgnoreCase(ext)) {
            startEpubSession();
            return;
        }

        long size = RichDocumentReader.queryOpenableSize(this, uri);
        if (RichDocumentReader.shouldStreamAsPlainText(ext, size)) {
            startStreamingText(size);
            return;
        }

        showLoading(true);
        executor.execute(() -> {
            try {
                final String html = reader.readDocumentAsHtml(uri, fileName);
                runOnUiThread(() -> {
                    webView.loadDataWithBaseURL(
                        null, html, "text/html; charset=UTF-8", "UTF-8", null
                    );
                    showLoading(false);
                    applyTheme();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(this, R.string.loading_error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void startEpubSession() {
        epubMode = true;
        epubEofReached = false;
        epubTempDeleted = false;
        epubNextChapter.set(0);
        epubFirstDomWrapper = 0;
        epubSession = null;
        showLoading(true);

        executor.execute(() -> {
            try {
                EpubReader.Session session = EpubReader.openSession(DocumentViewerActivity.this, uri);
                final String shell = EpubReader.buildEpubShellHtml(fileName, session);
                runOnUiThread(() -> {
                    epubSession = session;
                    webView.addJavascriptInterface(new EpubBridge(this), "EpubBridge");
                    webView.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            showLoading(false);
                            applyTheme();
                            executor.execute(DocumentViewerActivity.this::loadNextEpubChapter);
                        }
                    });
                    webView.loadDataWithBaseURL(null, shell, "text/html; charset=UTF-8", "UTF-8", null);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    void loadNextEpubChapter() {
        if (!epubMode || epubEofReached) {
            runOnUiThread(this::clearEpubLoadingJs);
            return;
        }
        EpubReader.Session session = epubSession;
        if (session == null) {
            runOnUiThread(this::clearEpubLoadingJs);
            return;
        }
        int idx = epubNextChapter.getAndIncrement();
        if (idx >= session.chapterCount) {
            epubEofReached = true;
            runOnUiThread(() -> {
                finishEpubFoot();
                clearEpubLoadingJs();
                deleteEpubTempFile();
            });
            return;
        }
        try {
            String path = session.chapterPaths.get(idx);
            String html = EpubReader.renderChapterBody(session.epubFile, idx, path);
            final String h = html;
            final int i = idx;
            final boolean isLast = (idx + 1 >= session.chapterCount);
            runOnUiThread(() -> {
                appendEpubChapter(i, h);
                if (isLast) {
                    epubEofReached = true;
                    finishEpubFoot();
                    deleteEpubTempFile();
                }
                clearEpubLoadingJs();
                webView.evaluateJavascript("window.__tryEpubFill&&window.__tryEpubFill()", null);
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                clearEpubLoadingJs();
            });
        }
    }

    private void appendEpubChapter(int idx, String html) {
        if (idx - epubFirstDomWrapper >= MAX_EPUB_DOM_WRAPPERS) {
            webView.evaluateJavascript(
                "window.__evictEpub && window.__evictEpub(" + epubFirstDomWrapper + ")", null);
            epubFirstDomWrapper++;
        }
        String quoted = JSONObject.quote(html);
        webView.evaluateJavascript(
            "window.__appendEpub && window.__appendEpub(" + idx + "," + quoted + ");" +
                "window.__tryEpubFill && window.__tryEpubFill();", null);
    }

    private void finishEpubFoot() {
        webView.evaluateJavascript(
            "window.__epubEof=true;window.__epubLoading=false;" +
                "window.__setEpubFoot && window.__setEpubFoot(" + JSONObject.quote(
                getString(R.string.epub_end_reached)) + ");", null);
    }

    private void clearEpubLoadingJs() {
        webView.evaluateJavascript("window.__epubLoading=false", null);
    }

    private void deleteEpubTempFile() {
        EpubReader.Session s = epubSession;
        if (s == null || epubTempDeleted) return;
        epubTempDeleted = true;
        //noinspection ResultOfMethodCallIgnored
        s.epubFile.delete();
    }

    private void startStreamingText(long knownSize) {
        streamingMode = true;
        streamJavaSyntax = "java".equals(extensionOf(fileName));
        streamEofReached = false;
        streamChunkId = 0;
        streamFirstDomChunk = 0;
        showLoading(true);

        executor.execute(() -> {
            InputStream in = null;
            try {
                in = getContentResolver().openInputStream(uri);
                if (in == null) throw new IllegalStateException("Не вдалося відкрити файл");
                streamReader = new Utf8StreamChunkReader(in);

                Utf8StreamChunkReader.Result first = streamReader.readChunk(RichDocumentReader.STREAM_CHUNK_BYTES);
                String firstText = "";
                boolean firstEof = true;
                if (first != null) {
                    firstText = first.text != null ? first.text : "";
                    firstEof = first.endOfStream;
                }

                final String ft = firstText;
                final boolean fe = firstEof;
                final long sz = knownSize;
                final String html = RichDocumentReader.buildStreamViewerHtml(fileName, sz, streamJavaSyntax);

                runOnUiThread(() -> {
                    webView.addJavascriptInterface(new StreamBridge(this), "StreamBridge");
                    webView.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            appendStreamChunk(0, ft);
                            showLoading(false);
                            applyTheme();
                            if (fe) {
                                streamEofReached = true;
                                finishStreamFoot();
                                executor.execute(() -> closeStreamReader());
                            } else {
                                webView.evaluateJavascript("window.__tryAutoFill&&window.__tryAutoFill()", null);
                            }
                        }
                    });
                    webView.loadDataWithBaseURL(null, html, "text/html; charset=UTF-8", "UTF-8", null);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
                if (streamReader != null) {
                    streamReader.closeQuietly();
                    streamReader = null;
                } else if (in != null) {
                    try { in.close(); } catch (Exception ignored) {}
                }
            }
        });
    }

    void loadNextStreamChunk() {
        if (!streamingMode || streamEofReached || streamReader == null) {
            runOnUiThread(this::clearStreamLoadingJs);
            return;
        }
        try {
            Utf8StreamChunkReader.Result r = streamReader.readChunk(RichDocumentReader.STREAM_CHUNK_BYTES);
            if (r == null) {
                streamEofReached = true;
                closeStreamReader();
                runOnUiThread(() -> {
                    finishStreamFoot();
                    clearStreamLoadingJs();
                });
                return;
            }
            final int id = ++streamChunkId;
            final String text = r.text != null ? r.text : "";
            final boolean eof = r.endOfStream;
            runOnUiThread(() -> {
                appendStreamChunk(id, text);
                if (eof) {
                    streamEofReached = true;
                    closeStreamReader();
                    finishStreamFoot();
                }
                clearStreamLoadingJs();
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                clearStreamLoadingJs();
            });
        }
    }

    private void appendStreamChunk(int id, String text) {
        if (id - streamFirstDomChunk >= RichDocumentReader.STREAM_MAX_DOM_CHUNKS) {
            webView.evaluateJavascript(
                "window.__evictChunk && window.__evictChunk(" + streamFirstDomChunk + ")", null);
            streamFirstDomChunk++;
        }
        String payload = streamJavaSyntax ? JavaSyntaxHighlighter.highlightToHtml(text) : text;
        String quoted = JSONObject.quote(payload);
        String asHtml = streamJavaSyntax ? "true" : "false";
        webView.evaluateJavascript(
            "window.__appendChunk && window.__appendChunk(" + id + "," + quoted + "," + asHtml + ");" +
                "window.__tryAutoFill && window.__tryAutoFill();", null);
    }

    private void finishStreamFoot() {
        webView.evaluateJavascript(
            "window.__streamEof=true;window.__streamLoading=false;" +
                "window.__setFoot && window.__setFoot(" + JSONObject.quote(
                getString(R.string.stream_end_reached)) + ");", null);
    }

    private void clearStreamLoadingJs() {
        webView.evaluateJavascript("window.__streamLoading=false", null);
    }

    private void closeStreamReader() {
        if (streamReader != null) {
            streamReader.closeQuietly();
            streamReader = null;
        }
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) return "";
        int i = fileName.lastIndexOf('.');
        return i < 0 ? "" : fileName.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void applyTheme() {
        String js = "(function(){document.body.classList." +
            (darkMode ? "add" : "remove") + "('theme-dark');})();";
        webView.evaluateJavascript(js, null);
    }

    private void changeZoom(int delta) {
        textZoom = Math.max(50, Math.min(300, textZoom + delta));
        webView.getSettings().setTextZoom(textZoom);
        prefs.edit().putInt(KEY_ZOOM, textZoom).apply();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_document_viewer, menu);
        MenuItem item = menu.findItem(R.id.action_dark_mode);
        if (item != null) item.setChecked(darkMode);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_search) {
            openSearch();
            return true;
        } else if (id == R.id.action_dark_mode) {
            darkMode = !darkMode;
            item.setChecked(darkMode);
            prefs.edit().putBoolean(KEY_DARK, darkMode).apply();
            applyTheme();
            return true;
        } else if (id == R.id.action_zoom_in) {
            changeZoom(+10);
            return true;
        } else if (id == R.id.action_zoom_out) {
            changeZoom(-10);
            return true;
        } else if (id == R.id.action_share) {
            shareCurrent();
            return true;
        } else if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void shareCurrent() {
        if (uri == null) return;
        try {
            android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
            send.setType(getContentResolver().getType(uri));
            send.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            send.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(send, getString(R.string.share)));
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (searchBar.getVisibility() == View.VISIBLE) {
            closeSearch();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdownNow();
        if (epubMode && epubSession != null && !epubTempDeleted) {
            epubTempDeleted = true;
            //noinspection ResultOfMethodCallIgnored
            epubSession.epubFile.delete();
        }
        epubSession = null;
        closeStreamReader();
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
    }

    /** Викликається з JavaScript при прокрутці до низу. */
    static final class StreamBridge {
        private final DocumentViewerActivity act;

        StreamBridge(DocumentViewerActivity activity) {
            this.act = activity;
        }

        @JavascriptInterface
        public void requestMore() {
            if (act.isFinishing()) return;
            act.executor.execute(() -> act.loadNextStreamChunk());
        }
    }

    static final class EpubBridge {
        private final DocumentViewerActivity act;

        EpubBridge(DocumentViewerActivity activity) {
            this.act = activity;
        }

        @JavascriptInterface
        public void requestMore() {
            if (act.isFinishing()) return;
            act.executor.execute(() -> act.loadNextEpubChapter());
        }
    }
}
