package com.example.thang4ngay3;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.example.thang4ngay3.api.ApiClient;
import com.example.thang4ngay3.api.ApiService;
import com.example.thang4ngay3.model.UploadResponse;
import com.example.thang4ngay3.utils.RealPathUtil;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;
import com.karumi.dexter.listener.single.PermissionListener;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_IMAGE_GALLERY = 101;
    private static final int REQUEST_IMAGE_CAMERA = 102;

    private ImageView imagePreview;
    private MaterialButton btnGallery, btnCamera, btnUpload;
    private TextInputEditText edtDescription;
    private ProgressBar progressBar;
    private FrameLayout loadingOverlay;
    private TextView tvPlaceholder;

    private Uri imageUri;
    private File imageFile;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Khởi tạo Views
        imagePreview = findViewById(R.id.imagePreview);
        btnGallery = findViewById(R.id.btnGallery);
        btnCamera = findViewById(R.id.btnCamera);
        btnUpload = findViewById(R.id.btnUpload);
        edtDescription = findViewById(R.id.edtDescription);
        progressBar = findViewById(R.id.progressBar);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        tvPlaceholder = findViewById(R.id.tvPlaceholder);

        // Khởi tạo API service
        apiService = ApiClient.getClient().create(ApiService.class);

        // Set click listeners
        btnGallery.setOnClickListener(v -> checkGalleryPermission());
        btnCamera.setOnClickListener(v -> checkCameraPermission());
        btnUpload.setOnClickListener(v -> uploadImage());

        // Vô hiệu hóa nút tải lên ban đầu
        btnUpload.setEnabled(false);
    }

    // Kiểm tra quyền truy cập thư viện
    private void checkGalleryPermission() {
        Dexter.withContext(this)
                .withPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse response) {
                        openGallery();
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse response) {
                        Toast.makeText(MainActivity.this, "Quyền truy cập thư viện bị từ chối", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).check();
    }

    // Kiểm tra quyền sử dụng camera
    private void checkCameraPermission() {
        Dexter.withContext(this)
                .withPermissions(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        if (report.areAllPermissionsGranted()) {
                            openCamera();
                        } else {
                            Toast.makeText(MainActivity.this, "Cần quyền truy cập camera", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).check();
    }

    // Mở thư viện ảnh
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_IMAGE_GALLERY);
    }

    // Mở camera
    private void openCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            imageFile = createImageFile();

            if (imageFile != null) {
                imageUri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        imageFile
                );

                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                startActivityForResult(cameraIntent, REQUEST_IMAGE_CAMERA);
            } else {
                Toast.makeText(this, "Không thể tạo file ảnh", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Không tìm thấy ứng dụng camera", Toast.LENGTH_SHORT).show();
        }
    }

    // Tạo file ảnh trước khi chụp
    private File createImageFile() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "JPEG_" + timeStamp + "_";
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            return File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (IOException e) {
            Log.e(TAG, "Lỗi tạo file ảnh", e);
            return null;
        }
    }

    // Xử lý kết quả từ thư viện ảnh hoặc camera
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_GALLERY) {
                // Nhận ảnh từ thư viện
                if (data != null) {
                    imageUri = data.getData();
                    String realPath = RealPathUtil.getRealPath(this, imageUri);
                    if (realPath != null) {
                        imageFile = new File(realPath);
                        loadImageIntoPreview();
                    } else {
                        Toast.makeText(this, "Không thể lấy đường dẫn ảnh", Toast.LENGTH_SHORT).show();
                    }
                }
            } else if (requestCode == REQUEST_IMAGE_CAMERA) {
                // Nhận ảnh từ camera - imageFile đã được tạo trong openCamera()
                loadImageIntoPreview();
            }
        }
    }

    // Hiển thị ảnh đã chọn
    private void loadImageIntoPreview() {
        if (imageUri != null) {
            Glide.with(this)
                    .load(imageUri)
                    .into(imagePreview);

            tvPlaceholder.setVisibility(View.GONE);
            btnUpload.setEnabled(true);
        }
    }

    // Upload ảnh lên server
    private void uploadImage() {
        if (imageFile == null) {
            Toast.makeText(this, "Vui lòng chọn ảnh trước", Toast.LENGTH_SHORT).show();
            return;
        }

        String description = edtDescription.getText() != null ? edtDescription.getText().toString() : "";

        loadingOverlay.setVisibility(View.VISIBLE);

        // Tạo RequestBody cho ảnh và mô tả
        RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), imageFile);
        MultipartBody.Part imagePart = MultipartBody.Part.createFormData("image", imageFile.getName(), requestFile);
        RequestBody descriptionBody = RequestBody.create(MediaType.parse("text/plain"), description);

        // Thực hiện upload
        Call<UploadResponse> call = apiService.uploadImage(imagePart, descriptionBody);
        call.enqueue(new Callback<UploadResponse>() {
            @Override
            public void onResponse(Call<UploadResponse> call, Response<UploadResponse> response) {
                loadingOverlay.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    UploadResponse uploadResponse = response.body();
                    if (uploadResponse.isSuccess()) {
                        Toast.makeText(MainActivity.this, "Upload thành công! " + uploadResponse.getMessage(), Toast.LENGTH_SHORT).show();

                        // Reset các fields
                        imagePreview.setImageResource(0);
                        tvPlaceholder.setVisibility(View.VISIBLE);
                        edtDescription.setText("");
                        imageFile = null;
                        imageUri = null;
                        btnUpload.setEnabled(false);
                    } else {
                        Toast.makeText(MainActivity.this, "Upload thất bại: " + uploadResponse.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    String errorMsg = "Upload thất bại, lỗi: " + response.code();
                    Log.e(TAG, errorMsg);
                    Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<UploadResponse> call, Throwable t) {
                loadingOverlay.setVisibility(View.GONE);
                String errorMsg = "Lỗi kết nối: " + t.getMessage();
                Log.e(TAG, errorMsg, t);
                Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}