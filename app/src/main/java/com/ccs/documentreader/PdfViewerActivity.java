package com.ccs.documentreader;

import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Швидкий PDF-перегляд на базі android.graphics.pdf.PdfRenderer.
 * Сторінки рендеряться "на льоту", з LRU-кешем bitmap'ів —
 * можна відкривати документи на сотні сторінок без OOM.
 */
public class PdfViewerActivity extends AppCompatActivity {

    public static final String EXTRA_FILE_URI = "file_uri";
    public static final String EXTRA_FILE_NAME = "file_name";

    private MaterialToolbar toolbar;
    private RecyclerView recycler;
    private ProgressBar progress;
    private TextView pageIndicator;

    private ParcelFileDescriptor descriptor;
    private PdfRenderer renderer;
    private File tempFile;

    private PdfPageAdapter adapter;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_viewer);

        toolbar = findViewById(R.id.toolbar);
        recycler = findViewById(R.id.recycler);
        progress = findViewById(R.id.progressBar);
        pageIndicator = findViewById(R.id.pageIndicator);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        String name = getIntent().getStringExtra(EXTRA_FILE_NAME);
        if (name != null) toolbar.setTitle(name);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setHasFixedSize(false);

        String uriStr = getIntent().getStringExtra(EXTRA_FILE_URI);
        if (uriStr == null) {
            Toast.makeText(this, R.string.loading_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        progress.setVisibility(View.VISIBLE);
        Uri uri = Uri.parse(uriStr);
        io.execute(() -> {
            try {
                openRenderer(uri);
                runOnUiThread(this::bindAdapter);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(this,
                        getString(R.string.pdf_open_failed) + ": " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_pdf_viewer, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_first_page) {
            recycler.smoothScrollToPosition(0);
            return true;
        } else if (id == R.id.action_last_page && renderer != null) {
            recycler.smoothScrollToPosition(renderer.getPageCount() - 1);
            return true;
        } else if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openRenderer(Uri uri) throws IOException {
        ParcelFileDescriptor pfd = openSeekableDescriptor(uri);
        descriptor = pfd;
        renderer = new PdfRenderer(pfd);
    }

    /**
     * PdfRenderer вимагає seekable file descriptor. Більшість content:// URIs його дають,
     * але деякі (наприклад завантаження з інтернету через провайдер) — ні.
     * У такому разі копіюємо потік у тимчасовий файл.
     */
    private ParcelFileDescriptor openSeekableDescriptor(Uri uri) throws IOException {
        ContentResolver cr = getContentResolver();
        try {
            ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "r");
            if (pfd != null) return pfd;
        } catch (Exception ignored) {
            // fallback нижче
        }
        // fallback: копія в кеш
        tempFile = File.createTempFile("doc_reader_", ".pdf", getCacheDir());
        try (InputStream is = cr.openInputStream(uri);
             OutputStream os = new FileOutputStream(tempFile)) {
            if (is == null) throw new IOException("Cannot open input stream");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = is.read(buf)) != -1) {
                os.write(buf, 0, n);
            }
        }
        return ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private void bindAdapter() {
        progress.setVisibility(View.GONE);
        if (renderer == null) return;
        adapter = new PdfPageAdapter(renderer);
        recycler.setAdapter(adapter);
        updateIndicator(0);
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int pos = lm.findFirstVisibleItemPosition();
                if (pos >= 0) updateIndicator(pos);
            }
        });
    }

    private void updateIndicator(int pos) {
        if (renderer == null) return;
        pageIndicator.setText(getString(R.string.pdf_page_indicator,
            pos + 1, renderer.getPageCount()));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
        if (adapter != null) adapter.recycleAll();
        try { if (renderer != null) renderer.close(); } catch (Exception ignored) {}
        try { if (descriptor != null) descriptor.close(); } catch (Exception ignored) {}
        if (tempFile != null && tempFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tempFile.delete();
        }
    }

    /** Static helper для зручного запуску. */
    public static void start(android.content.Context ctx, Uri uri, String name) {
        Intent i = new Intent(ctx, PdfViewerActivity.class);
        i.putExtra(EXTRA_FILE_URI, uri.toString());
        i.putExtra(EXTRA_FILE_NAME, name);
        ctx.startActivity(i);
    }

    // ===================== Adapter =====================

    static final class PdfPageAdapter extends RecyclerView.Adapter<PageVH> {

        private final PdfRenderer renderer;
        private final LruCache<Integer, Bitmap> cache;

        PdfPageAdapter(PdfRenderer renderer) {
            this.renderer = renderer;
            int cacheSize = (int) (Runtime.getRuntime().maxMemory() / 1024 / 6);
            this.cache = new LruCache<Integer, Bitmap>(cacheSize) {
                @Override
                protected int sizeOf(@NonNull Integer key, @NonNull Bitmap value) {
                    return value.getByteCount() / 1024;
                }
                @Override
                protected void entryRemoved(boolean evicted, @NonNull Integer key,
                                            @NonNull Bitmap oldValue, @Nullable Bitmap newValue) {
                    if (evicted && oldValue != null && !oldValue.isRecycled()) {
                        oldValue.recycle();
                    }
                }
            };
        }

        @NonNull
        @Override
        public PageVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pdf_page, parent, false);
            return new PageVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull PageVH h, int position) {
            h.label.setText(h.itemView.getContext().getString(
                R.string.pdf_page_label, position + 1));
            Bitmap cached = cache.get(position);
            if (cached != null && !cached.isRecycled()) {
                h.image.setImageBitmap(cached);
                return;
            }
            h.image.setImageBitmap(null);
            try {
                PdfRenderer.Page page = renderer.openPage(position);
                int targetWidth = h.itemView.getResources().getDisplayMetrics().widthPixels;
                float scale = (float) targetWidth / page.getWidth();
                int targetHeight = Math.round(page.getHeight() * scale);
                Bitmap bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
                bmp.eraseColor(Color.WHITE);
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close();
                cache.put(position, bmp);
                h.image.setImageBitmap(bmp);
            } catch (Exception e) {
                h.label.setText(h.itemView.getContext().getString(
                    R.string.pdf_page_error, position + 1, e.getMessage()));
            }
        }

        @Override
        public int getItemCount() {
            return renderer.getPageCount();
        }

        void recycleAll() {
            cache.evictAll();
        }
    }

    static final class PageVH extends RecyclerView.ViewHolder {
        final TextView label;
        final ImageView image;

        PageVH(@NonNull View v) {
            super(v);
            label = v.findViewById(R.id.pageLabel);
            image = v.findViewById(R.id.pageImage);
        }
    }
}
