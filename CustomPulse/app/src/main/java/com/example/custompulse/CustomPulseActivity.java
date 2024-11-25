package com.example.custompulse;

import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import androidx.core.app.ActivityCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class CustomPulseActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;
    private ExecutorService cameraExecutor;
    private boolean faceDetected = false; // 标记是否有人脸
    private PreviewView textureView;
    private long lastAnalysisTime = 0;  // 上次分析的时间戳
    private FaceCrossView faceCrossView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = findViewById(R.id.viewFinder);
        faceCrossView = findViewById(R.id.faceCrossView);  // 初始化 FaceCrossView
        cameraExecutor = Executors.newSingleThreadExecutor();

        // 检查权限
        if (checkCameraPermission()) {
            startCamera(textureView);
        } else {
            requestCameraPermission();
        }
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST_CODE
        );
    }

    private void startCamera(PreviewView textureView) {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                // 获取 CameraProvider
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // 创建 CameraSelector，选择前置摄像头
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();

                // 配置 Preview，设置更低的分辨率以提高性能
                Preview preview = new Preview.Builder()
                        .setTargetResolution(new Size(640, 640))  // 设置较低的预览分辨率
                        .build();

                // 将 PreviewView 作为预览的输出目标
                preview.setSurfaceProvider(textureView.getSurfaceProvider());

                // 配置 ImageAnalysis，设置分析的分辨率并减少分析频率
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 640))  // 设置较低的分析分辨率
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // 保留最新图像
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                // 绑定用例到 CameraX 的生命周期
                cameraProvider.unbindAll();
                Camera camera = cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageAnalysis);

                // 打印当前选择的分辨率
                Log.d("CameraResolution", "当前选择的分辨率: 640x640");

            } catch (Exception e) {
                Log.e("CameraX", "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeImage(ImageProxy imageProxy) {
        long timestamp = System.currentTimeMillis();
        if (timestamp - lastAnalysisTime < 500) {
            imageProxy.close();
            return;
        }
        lastAnalysisTime = timestamp;

        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees());

        // 配置人脸检测器
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build();

        FaceDetector detector = FaceDetection.getClient(options);

        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (!faces.isEmpty()) {
                        com.google.mlkit.vision.face.Face largestFace = null;
                        float maxArea = 0;

                        // 找出最大人脸
                        for (com.google.mlkit.vision.face.Face face : faces) {
                            Rect boundingBox = face.getBoundingBox();
                            float area = boundingBox.width() * boundingBox.height();
                            if (area > maxArea) {
                                maxArea = area;
                                largestFace = face;
                            }
                        }

                        if (largestFace != null) {
                            Rect boundingBox = largestFace.getBoundingBox();
                            float centerX = boundingBox.centerX();
                            float centerY = boundingBox.centerY();
                            Size previewSize = new Size(textureView.getWidth(), textureView.getHeight());
                            int faceWidth = boundingBox.width();
                            int faceHeight = boundingBox.height();

                            // 判断最大人脸是否位于屏幕中央并满足面积要求
                            if (isFaceCentered(centerX, centerY, previewSize) &&
                                    faceWidth * faceHeight > previewSize.getWidth() * previewSize.getHeight() / 15) {
                                faceCrossView.updateFacePosition(centerX, centerY, faceWidth, faceHeight); // 更新十字架
                                if (!faceDetected) {
                                    faceDetected = true;
                                    onFaceDetected();
                                }
                            } else {
                                faceCrossView.hideCross();  // 隐藏十字架
                                if (faceDetected) {
                                    faceDetected = false;
                                    onFaceLost();
                                }
                            }
                        }
                    } else {
                        // 未检测到人脸
                        if (faceDetected) {
                            faceDetected = false;
                            onFaceLost();
                        }
                        faceCrossView.hideCross(); // 确保隐藏十字架
                    }
                })
                .addOnFailureListener(e -> Log.e("FaceDetection", "Detection failed", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }


    private boolean isFaceCentered(float centerX, float centerY, Size previewSize) {
        // 获取PreviewView的中心点
        float previewCenterX = previewSize.getWidth() / 2;
        float previewCenterY = previewSize.getHeight() / 2;

        // 判断人脸中心是否在PreviewView的中央（±30%范围）
        return Math.abs(centerX - previewCenterX) < previewSize.getWidth() * 0.3 &&
                Math.abs(centerY - previewCenterY) < previewSize.getHeight() * 0.3;
    }

    private void onFaceDetected() {
        runOnUiThread(() -> Log.e("cys","人脸检测到了"));
    }

    private void onFaceLost() {
        runOnUiThread(() -> Log.e("cys","人脸检测到了"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限被授予，启动摄像头
                startCamera(textureView);
            } else {
                // 权限被拒绝，提示用户
                Toast.makeText(this, "摄像头权限被拒绝，无法使用摄像头功能！", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
