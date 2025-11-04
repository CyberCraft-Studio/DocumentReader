package com.ccs.documentreader;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private TextView tvFileName;
    private Button btnSelectFile;
    
    private final ActivityResultLauncher<Intent> filePickerLauncher = 
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    String fileName = getFileName(uri);
                    loadDocument(uri, fileName);
                }
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        checkPermissions();
        setupListeners();
    }
    
    private void initViews() {
        tvFileName = findViewById(R.id.tvFileName);
        btnSelectFile = findViewById(R.id.btnSelectFile);
    }
    
    private void setupListeners() {
        btnSelectFile.setOnClickListener(v -> openFilePicker());
    }
    
    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+)
            // Використовуємо пряме значення для сумісності
            String readMediaDocuments = "android.permission.READ_MEDIA_DOCUMENTS";
            if (ContextCompat.checkSelfPermission(this, readMediaDocuments) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{readMediaDocuments},
                        PERMISSION_REQUEST_CODE);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0 - 12 (API 23-32)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        // Додавання MIME типів для різних форматів
        String[] mimeTypes = {
            "text/plain",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/zip",
            "application/x-tar",
            "application/json",
            "application/xml",
            "text/xml",
            "text/csv",
            "text/markdown",
            "application/epub+zip",
            "application/x-mobipocket-ebook",
            "*/*"
        };
        
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        
        try {
            filePickerLauncher.launch(Intent.createChooser(intent, "Select document"));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, R.string.install_file_manager, Toast.LENGTH_SHORT).show();
        }
    }
    
    private void loadDocument(Uri uri, String fileName) {
        // Відкриваємо окреме Activity для перегляду документа
        Intent intent = new Intent(this, DocumentViewerActivity.class);
        intent.putExtra(DocumentViewerActivity.EXTRA_FILE_URI, uri.toString());
        intent.putExtra(DocumentViewerActivity.EXTRA_FILE_NAME, fileName);
        startActivity(intent);
    }
    
    private String getFileName(Uri uri) {
        String fileName = "document";
        
        try {
            String path = uri.getPath();
            if (path != null && path.contains("/")) {
                fileName = path.substring(path.lastIndexOf("/") + 1);
            }
            
            // Альтернативний метод через ContentResolver
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                        if (nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex);
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return fileName;
    }
}