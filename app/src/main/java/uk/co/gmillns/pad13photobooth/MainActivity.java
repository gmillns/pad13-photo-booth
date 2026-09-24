package uk.co.gmillns.pad13photobooth;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private PreviewView previewView;
    private ImageView photoPreview;
    private TextView countdown, message;
    private LinearLayout cameraControls, reviewControls;
    private ImageCapture imageCapture;
    private File pendingPhoto;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show();
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        hideSystemUi();

        previewView = findViewById(R.id.previewView);
        photoPreview = findViewById(R.id.photoPreview);
        countdown = findViewById(R.id.countdown);
        message = findViewById(R.id.message);
        cameraControls = findViewById(R.id.cameraControls);
        reviewControls = findViewById(R.id.reviewControls);
        Button take = findViewById(R.id.takePhoto);
        Button retake = findViewById(R.id.retake);
        Button keep = findViewById(R.id.keep);

        take.setOnClickListener(v -> beginCountdown());
        retake.setOnClickListener(v -> resetToCamera());
        keep.setOnClickListener(v -> keepPhoto());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            startCamera();
        else cameraPermission.launch(Manifest.permission.CAMERA);
    }

    private void hideSystemUi() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build();
                provider.unbindAll();
                CameraSelector selector = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                        ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
                provider.bindToLifecycle(this, selector, preview, imageCapture);
            } catch (Exception e) {
                Toast.makeText(this, "Unable to start camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void beginCountdown() {
        cameraControls.setVisibility(View.GONE);
        countdown.setVisibility(View.VISIBLE);
        showCount(3);
    }

    private void showCount(int n) {
        if (n == 0) {
            countdown.setVisibility(View.GONE);
            capturePhoto();
            return;
        }
        countdown.setText(String.valueOf(n));
        handler.postDelayed(() -> showCount(n - 1), 850);
    }

    private void capturePhoto() {
        if (imageCapture == null) { resetToCamera(); return; }
        pendingPhoto = new File(getCacheDir(), "booth_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(pendingPhoto).build();
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                photoPreview.setImageURI(Uri.fromFile(pendingPhoto));
                photoPreview.setVisibility(View.VISIBLE);
                previewView.setVisibility(View.GONE);
                reviewControls.setVisibility(View.VISIBLE);
            }
            @Override public void onError(@NonNull ImageCaptureException exc) {
                Toast.makeText(MainActivity.this, "Photo failed: " + exc.getMessage(), Toast.LENGTH_LONG).show();
                resetToCamera();
            }
        });
    }

    private void keepPhoto() {
        if (pendingPhoto == null || !pendingPhoto.exists()) return;
        try {
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.UK).format(new Date());
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "PhotoBooth_" + stamp + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LadiesNightPhotoBooth");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("Unable to create image");
            try (FileInputStream in = new FileInputStream(pendingPhoto);
                 OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new Exception("Unable to open image");
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) > 0) out.write(buffer, 0, len);
            }
            pendingPhoto.delete();
            pendingPhoto = null;
            reviewControls.setVisibility(View.GONE);
            photoPreview.setVisibility(View.GONE);
            message.setText("THANK YOU!\nYour photo has been saved.");
            message.setVisibility(View.VISIBLE);
            handler.postDelayed(this::resetToCamera, 2500);
        } catch (Exception e) {
            Toast.makeText(this, "Could not save photo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void resetToCamera() {
        if (pendingPhoto != null && pendingPhoto.exists()) pendingPhoto.delete();
        pendingPhoto = null;
        photoPreview.setImageDrawable(null);
        photoPreview.setVisibility(View.GONE);
        reviewControls.setVisibility(View.GONE);
        message.setVisibility(View.GONE);
        countdown.setVisibility(View.GONE);
        previewView.setVisibility(View.VISIBLE);
        cameraControls.setVisibility(View.VISIBLE);
        hideSystemUi();
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemUi();
    }
}
