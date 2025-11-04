package com.ccs.documentreader;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity для відображення документів з форматуванням
 */
public class DocumentViewerActivity extends AppCompatActivity {

    public static final String EXTRA_FILE_URI = "file_uri";
    public static final String EXTRA_FILE_NAME = "file_name";

    private WebView webView;
    private ProgressBar progressBar;
    private MaterialToolbar toolbar;
    private Button btnZoomIn, btnZoomOut, btnZoomReset;

    private RichDocumentReader documentReader;
    private ExecutorService executorService;
    
    private int currentZoom = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_document_viewer);

        initViews();
        setupWebView();
        setupToolbar();
        setupZoomControls();
        
        documentReader = new RichDocumentReader(this);
        executorService = Executors.newSingleThreadExecutor();

        loadDocument();
    }

    private void initViews() {
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        toolbar = findViewById(R.id.toolbar);
        btnZoomIn = findViewById(R.id.btnZoomIn);
        btnZoomOut = findViewById(R.id.btnZoomOut);
        btnZoomReset = findViewById(R.id.btnZoomReset);
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false); // Безпека
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setDefaultTextEncodingName("UTF-8");
        
        // Підтримка зображень (включаючи base64)
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(true); // Блокувати мережу, але дозволити base64
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        
        // Дозволити file:// для base64
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        
        // Для кращого відображення
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
        settings.setTextZoom(100);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        toolbar.setNavigationOnClickListener(v -> finish());

        // Встановити заголовок з назви файлу
        String fileName = getIntent().getStringExtra(EXTRA_FILE_NAME);
        if (fileName != null) {
            toolbar.setTitle(fileName);
        }
    }

    private void setupZoomControls() {
        btnZoomIn.setOnClickListener(v -> {
            if (currentZoom < 200) {
                currentZoom += 10;
                updateZoom();
            }
        });

        btnZoomOut.setOnClickListener(v -> {
            if (currentZoom > 50) {
                currentZoom -= 10;
                updateZoom();
            }
        });

        btnZoomReset.setOnClickListener(v -> {
            currentZoom = 100;
            updateZoom();
        });
    }

    private void updateZoom() {
        webView.setInitialScale(currentZoom);
        btnZoomReset.setText(currentZoom + "%");
    }

    private void loadDocument() {
        String uriString = getIntent().getStringExtra(EXTRA_FILE_URI);
        String fileName = getIntent().getStringExtra(EXTRA_FILE_NAME);

        if (uriString == null || fileName == null) {
            Toast.makeText(this, "Error: no file data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Uri uri = Uri.parse(uriString);
        showLoading(true);

        executorService.execute(() -> {
            try {
                String htmlContent = documentReader.readDocumentAsHtml(uri, fileName);

                runOnUiThread(() -> {
                    webView.loadDataWithBaseURL(
                        "file:///android_asset/",
                        htmlContent,
                        "text/html; charset=UTF-8",
                        "UTF-8",
                        null
                    );
                    showLoading(false);
                    Toast.makeText(this, R.string.document_loaded, Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    showError(e.getMessage());
                    showLoading(false);
                });
            }
        });
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        webView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showError(String message) {
        String errorHtml = "<!DOCTYPE html><html><head>" +
                "<meta charset='UTF-8'>" +
                "<style>" +
                "body { font-family: sans-serif; padding: 20px; background: #ffebee; }" +
                ".error { background: white; padding: 20px; border-radius: 8px; " +
                "border-left: 4px solid #f44336; }" +
                "h2 { color: #c62828; }" +
                "</style></head><body>" +
                "<div class='error'>" +
                "<h2>❌ Loading Error</h2>" +
                "<p>" + message + "</p>" +
                "</div></body></html>";

        webView.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null);
        Toast.makeText(this, R.string.loading_error, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        if (webView != null) {
            webView.destroy();
        }
    }
}

