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
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
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

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import android.os.Handler;
import android.os.Looper;

import android.os.Handler;
import android.os.Looper;

public class CustomPulseActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1001;
    private ExecutorService cameraExecutor;
    private boolean faceDetected = false; // 标记是否有人脸
    private PreviewView textureView;
    private long lastAnalysisTime = 0;  // 上次分析的时间戳
    private FaceCrossView faceCrossView;
    private VideoCapture<Recorder> videoCapture;
    private Recording currentRecording;
    private Handler recordingHandler; // 用于定时任务
    private Runnable stopRecordingRunnable; // 定时任务逻辑
    private long recordingStartTime = 0; // 记录录制开始的时间戳
    public static final int RecordDuration =5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = findViewById(R.id.viewFinder);
        faceCrossView = findViewById(R.id.faceCrossView); // 初始化 FaceCrossView
        cameraExecutor = Executors.newSingleThreadExecutor();
        recordingHandler = new Handler(Looper.getMainLooper()); // 初始化 Handler

        // 检查权限
        if (checkCameraPermission()) {
            startCamera(textureView);
        } else {
            requestCameraPermission();
        }
    }
    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
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

                // 配置 VideoCapture（新的 API）
                Recorder recorder = new Recorder.Builder()
                        .build();

                videoCapture = VideoCapture.withOutput(recorder);

                // 绑定所有用例到 CameraX 的生命周期
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageAnalysis, videoCapture);

                Log.d("CameraResolution", "当前选择的分辨率: 640x640");

            } catch (Exception e) {
                Log.e("CameraX", "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // 权限回调处理
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                // 权限被授予，启动摄像头
                startCamera(textureView);
            } else {
                // 权限被拒绝，提示用户
                Toast.makeText(this, "摄像头或麦克风权限被拒绝，无法使用该功能！", Toast.LENGTH_SHORT).show();
            }
        }
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

    private void onFaceDetected() {
        runOnUiThread(() -> {
            Log.e("FaceDetection", "Face detected");
            if (currentRecording == null) {
                startRecording();
            }
        });
    }

    private boolean isFaceCentered(float centerX, float centerY, Size previewSize) {
        // 获取 PreviewView 的中心点
        float previewCenterX = previewSize.getWidth() / 2;
        float previewCenterY = previewSize.getHeight() / 2;

        // 判断人脸中心是否在 PreviewView 的中央（±30% 范围）
        return Math.abs(centerX - previewCenterX) < previewSize.getWidth() * 0.3 &&
                Math.abs(centerY - previewCenterY) < previewSize.getHeight() * 0.3;
    }
    private void onFaceLost() {
        runOnUiThread(() -> {
            Log.e("FaceDetection", "Face lost");
            if (currentRecording != null && System.currentTimeMillis() - recordingStartTime < RecordDuration) {
                stopRecording(true); // 在 15 秒内丢失人脸，删除文件
            }
        });
    }

    private void startRecording() {
        if (videoCapture == null) return;

        File videoFile = new File(getExternalFilesDir(null), "recorded_video.mp4");
        if (videoFile.exists() && videoFile.delete()) {
            Log.e("VideoCapture", "Temporary file deleted.");
        }
        FileOutputOptions outputOptions = new FileOutputOptions.Builder(videoFile).build();

        currentRecording = videoCapture.getOutput()
                .prepareRecording(this, outputOptions)
                .start(ContextCompat.getMainExecutor(this), event -> {
                    if (event instanceof VideoRecordEvent.Start) {
                        Log.e("VideoCapture", "Recording started");
                    } else if (event instanceof VideoRecordEvent.Finalize) {
                        Log.e("VideoCapture", "Recording finalized: " +
                                ((VideoRecordEvent.Finalize) event).getOutputResults().getOutputUri());
                    }
                });

        // 设置录制开始的时间
        recordingStartTime = System.currentTimeMillis();

        // 启动定时任务，15秒后停止录制
        stopRecordingRunnable = () -> {
            if (currentRecording != null) {
                stopRecording(false); // 超过15秒，停止录制并保留文件
            }
        };
        recordingHandler.postDelayed(stopRecordingRunnable, RecordDuration);
    }

    private void stopRecording(boolean isDelete) {
        if (currentRecording != null) {
            currentRecording.stop();
            currentRecording = null;

            // 删除定时任务，避免重复执行
            if (stopRecordingRunnable != null) {
                recordingHandler.removeCallbacks(stopRecordingRunnable);
            }

            if (isDelete) {
                File videoFile = new File(getExternalFilesDir(null), "recorded_video.mp4");
                if (videoFile.exists() && videoFile.delete()) {
                    Log.e("VideoCapture", "Temporary file deleted.");
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (recordingHandler != null) {
            recordingHandler.removeCallbacksAndMessages(null);
        }
    }
}
