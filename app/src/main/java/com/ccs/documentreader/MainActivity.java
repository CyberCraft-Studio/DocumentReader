package com.ccs.documentreader;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private TextView tvFileName;
    private TextView tvEmptyRecent;
    private Button btnSelectFile;
    private RecyclerView recentList;

    private RecentFilesManager recents;
    private RecentAdapter adapter;

    private final ActivityResultLauncher<Intent> filePicker =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    persistUriPermission(uri);
                    openDocument(uri, queryFileName(uri));
                }
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvFileName = findViewById(R.id.tvFileName);
        tvEmptyRecent = findViewById(R.id.tvEmptyRecent);
        btnSelectFile = findViewById(R.id.btnSelectFile);
        recentList = findViewById(R.id.recentList);

        recents = new RecentFilesManager(this);
        adapter = new RecentAdapter();
        recentList.setLayoutManager(new LinearLayoutManager(this));
        recentList.setAdapter(adapter);

        btnSelectFile.setOnClickListener(v -> openFilePicker());

        checkPermissions();
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRecent();
    }

    private void refreshRecent() {
        List<RecentFilesManager.RecentFile> all = recents.getAll();
        adapter.submit(all);
        if (all.isEmpty()) {
            tvEmptyRecent.setVisibility(View.VISIBLE);
            recentList.setVisibility(View.GONE);
        } else {
            tvEmptyRecent.setVisibility(View.GONE);
            recentList.setVisibility(View.VISIBLE);
        }
    }

    private void handleIncomingIntent(@Nullable Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_SEND.equals(action)) {
            Uri uri = intent.getData();
            if (uri == null) {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
            if (uri != null) {
                persistUriPermission(uri);
                openDocument(uri, queryFileName(uri));
            }
        }
    }

    private void persistUriPermission(Uri uri) {
        try {
            int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) {
            // деякі провайдери не підтримують persistable uri permission — це нормально
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_clear_recent) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.recent_files)
                .setMessage(R.string.clear_recent_confirm)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    recents.clear();
                    refreshRecent();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return true;
        } else if (id == R.id.action_about) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.about_text)
                .setPositiveButton(android.R.string.ok, null)
                .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return; // Android 13+: працюємо лише з content:// URI, спецдозвіл не потрібен
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(code, permissions, grantResults);
        if (code == PERMISSION_REQUEST_CODE) {
            int msg = (grantResults.length > 0
                       && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                ? R.string.permission_granted : R.string.permission_denied;
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                      | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
            "text/*",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/epub+zip",
            "application/zip",
            "application/json",
            "application/xml",
            "application/x-yaml",
            "application/rtf"
        });
        try {
            filePicker.launch(intent);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, R.string.install_file_manager, Toast.LENGTH_SHORT).show();
        }
    }

    private void openDocument(Uri uri, String fileName) {
        if (uri == null || fileName == null) return;
        recents.add(uri, fileName);
        tvFileName.setText(fileName);

        String routedName = augmentNameFromMimeIfNeeded(this, uri, fileName);
        Intent i;
        if (shouldOpenAsPdf(uri, routedName)) {
            i = new Intent(this, PdfViewerActivity.class);
            i.putExtra(PdfViewerActivity.EXTRA_FILE_URI, uri.toString());
            i.putExtra(PdfViewerActivity.EXTRA_FILE_NAME, fileName);
        } else {
            i = new Intent(this, DocumentViewerActivity.class);
            i.putExtra(DocumentViewerActivity.EXTRA_FILE_URI, uri.toString());
            i.putExtra(DocumentViewerActivity.EXTRA_FILE_NAME, routedName);
        }
        startActivity(i);
    }

    private boolean shouldOpenAsPdf(Uri uri, String fileName) {
        if (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return true;
        }
        try {
            String mt = getContentResolver().getType(uri);
            return mt != null && "application/pdf".equalsIgnoreCase(mt);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Якщо у імені немає розширення (часто з «Поділитися»), додаємо .pdf / .docx тощо за MIME —
     * інакше {@link DocumentViewerActivity} не знатиме, який парсер викликати.
     */
    private static String augmentNameFromMimeIfNeeded(Context ctx, Uri uri, String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName;
        }
        String mime = null;
        try {
            mime = ctx.getContentResolver().getType(uri);
        } catch (Exception ignored) {}
        String ext = mimeToExtension(mime);
        if (ext == null) {
            return fileName != null ? fileName : "document";
        }
        String base = (fileName != null && !fileName.isEmpty()) ? fileName : "document";
        return base + "." + ext;
    }

    private static String mimeToExtension(String mime) {
        if (mime == null) return null;
        mime = mime.toLowerCase(Locale.ROOT);
        switch (mime) {
            case "application/pdf":
                return "pdf";
            case "application/msword":
                return "doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                return "docx";
            case "application/vnd.ms-powerpoint":
                return "ppt";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation":
                return "pptx";
            case "application/vnd.openxmlformats-officedocument.presentationml.slideshow":
                return "pptx";
            case "text/plain":
                return "txt";
            default:
                if (mime.startsWith("text/")) return "txt";
                return null;
        }
    }

    private String queryFileName(Uri uri) {
        String name = "document";
        try {
            try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        String s = c.getString(idx);
                        if (s != null && !s.isEmpty()) name = s;
                    }
                }
            }
        } catch (Exception ignored) {}
        if ("document".equals(name)) {
            String path = uri.getLastPathSegment();
            if (path != null) {
                int slash = path.lastIndexOf('/');
                name = slash >= 0 ? path.substring(slash + 1) : path;
            }
        }
        return name;
    }

    // ============== Recent Files RecyclerView ==============

    private final class RecentAdapter extends RecyclerView.Adapter<RecentVH> {

        private List<RecentFilesManager.RecentFile> items = Collections.emptyList();
        private final DateFormat fmt = DateFormat.getDateTimeInstance(
            DateFormat.SHORT, DateFormat.SHORT);

        void submit(List<RecentFilesManager.RecentFile> data) {
            items = data;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public RecentVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_file, parent, false);
            return new RecentVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecentVH h, int pos) {
            RecentFilesManager.RecentFile rf = items.get(pos);
            h.name.setText(rf.name);
            h.subtitle.setText(fmt.format(new Date(rf.openedAt)));
            h.itemView.setOnClickListener(v -> openDocument(Uri.parse(rf.uri), rf.name));
            h.btnRemove.setOnClickListener(v -> {
                recents.remove(rf.uri);
                refreshRecent();
            });
        }

        @Override
        public int getItemCount() { return items.size(); }
    }

    private static final class RecentVH extends RecyclerView.ViewHolder {
        final TextView name, subtitle;
        final ImageButton btnRemove;

        RecentVH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.recentName);
            subtitle = v.findViewById(R.id.recentSubtitle);
            btnRemove = v.findViewById(R.id.recentRemove);
        }
    }
}
