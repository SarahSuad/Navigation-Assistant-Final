package com.example.camera_segmentation_app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

class OutSections {
    int topLeftx;
    int topLefty;
    int topRightx;
    int topRighty;
    int botLeftx;
    int botLefty;
    int botRightx;
    int botRighty;

    public OutSections(int topLeftx, int topLefty, int topRightx, int topRighty, int botLeftx, int botLefty, int botRightx, int botRighty) {
        this.topLeftx = topLeftx;
        this.topLefty = topLefty;
        this.topRightx = topRightx;
        this.topRighty = topRighty;
        this.botLeftx = botLeftx;
        this.botLefty = botLefty;
        this.botRightx = botRightx;
        this.botRighty = botRighty;
    }
}

public class MainActivity extends AppCompatActivity {

    private Bitmap mSegmImage;
    private TextToSpeech mTTs;

    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private TextureView mPreview;
    private ImageView mMiniView;
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            setupCamera(width, height);
            connectCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    private CameraDevice mCamera;
    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCamera = camera;
            startPreview();
            Toast.makeText(getApplicationContext(), "Camera connected successfully!", Toast.LENGTH_SHORT).show();

            final ImageSegmenter Segmenter = new ImageSegmenter(MainActivity.this);

            // Secondary thread to process the segmentation without blocking up the main.
            final Handler mHandler = new Handler(getMainLooper());
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Gets the preview image and places it in a bitmap object called
                    Bitmap fullimg = mPreview.getBitmap();
                    Bitmap img = Bitmap.createScaledBitmap(fullimg, 225, 225, true);

                    // Segmenter.segmentFrame(img)
                    // This is where the function to carry out the segmentation goes
                    int[] outputArray = Segmenter.segmentFrame(img);

                    Log.d("OUT", Arrays.toString(outputArray));

                    int width = 225;
                    int height = 225;

                    Bitmap outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

                    ImageSegmenter.Model model= Segmenter.getCurrentModel();
                    Utils.Companion.segmentResultToBitmap(outputArray, model.getColors(), outputBitmap);
                    mMiniView= findViewById(R.id.MiniView);
                    mMiniView.setImageBitmap(Utils.Companion.resizeBitmap(outputBitmap, width, height));

                    // This would take the output of the segmentation, then convert it to audio
                    voiceOutput(segmentToVoice(outputBitmap));

                    mHandler.postDelayed(this, 1000);
                }
            }, 1000);

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            mCamera = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int i) {
            camera.close();
            mCamera = null;
        }
    };

    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;

    private void startBackgroundThread() {
        mBackgroundHandlerThread = new HandlerThread("Camera");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundHandlerThread.quitSafely();
        try {
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private String mCameraID;
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private Size mPreviewSize;
    private CaptureRequest.Builder mCaptureRequestBuilder;

    private static class CompareSizeByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) (lhs.getWidth() * lhs.getHeight()) -
                    (long) (rhs.getWidth() * rhs.getHeight()));
        }
    }


    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation) {
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);
        return (sensorOrientation + deviceOrientation + 360) % 360;
    }

    @Override
    protected void onCreate (Bundle savedInstanceState)  {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPreview = (TextureView) findViewById(R.id.PreviewView);

        mTTs= new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {
                if(i!= TextToSpeech.ERROR){
                    mTTs.setLanguage(Locale.US);

                }
                else{
                    Toast.makeText(MainActivity.this,"ERROR",Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();

        if (mPreview.isAvailable()) {
            setupCamera(mPreview.getWidth(), mPreview.getHeight());
            connectCamera();
        } else {
            mPreview.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }


    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CAMERA_PERMISSION_RESULT) {
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(), "Application won't run without Permissions.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        View decorView = getWindow().getDecorView();
        if (hasFocus) {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }

    }

    private void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraID : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraID);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
                int totalRotation = sensorToDeviceRotation(cameraCharacteristics, deviceOrientation);
                boolean swapRotation = totalRotation == 90 || totalRotation == 270;
                int rotatedWidth = width;
                int rotatedHeight = height;
                if (swapRotation) {
                    rotatedWidth = height;
                    rotatedHeight = width;
                }
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);
                mCameraID = cameraID;
                return;

            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    @SuppressLint("MissingPermission")
    private void connectCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(mCameraID, mCameraDeviceStateCallback, mBackgroundHandler);
                } else {
                    if(shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA)) {
                        Toast.makeText(this,
                                "Video app required access to camera", Toast.LENGTH_SHORT).show();
                    }
                    requestPermissions(new String[] {android.Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO
                    }, REQUEST_CAMERA_PERMISSION_RESULT);
                }

            } else {
                cameraManager.openCamera(mCameraID, mCameraDeviceStateCallback, mBackgroundHandler);
            }        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startPreview() {
        SurfaceTexture surfaceTexture = mPreview.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        try {
            mCaptureRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);

            mCamera.createCaptureSession(Arrays.asList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            try {
                                session.setRepeatingRequest(mCaptureRequestBuilder.build(), null, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(getApplicationContext(), "Unable to setup Preview", Toast.LENGTH_SHORT).show();
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if(mCamera != null) {
            mCamera.close();
        }
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height) {

        List<Size> bigEnough = new ArrayList<Size>();
        for(Size option : choices) {
            if(option.getHeight() == option.getWidth() * height / width &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if(bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizeByArea());
        } else {
            return choices[0];
        }
    }

    public int segmentToVoice(Bitmap segmOut) {

        int point_count = 0;
        int w = segmOut.getWidth();
        int h = segmOut.getHeight();
        int[] pxs = new int[w*h];
        String img_width = Integer.toString(w);
        String img_height = Integer.toString(h);

        segmOut.getPixels(pxs, 0, w, 0, 0, w, h);

        Log.d("IMG", Arrays.toString(pxs));

        // Color pixelColor = segmOut.getColor(w/2, h/2);

        int pixelColor = segmOut.getPixel(w/2, h/2);

        Toast.makeText(getApplicationContext(), "Width=" + img_width + "Height=" + img_height, Toast.LENGTH_SHORT).show();
        // colour for floor = (128, 64, 128)

        // |      S1      |
        // | S2 | S3 | S4 |
        // | S5 | S6 | S7 |

        int topleftx = 150;
        int toplefty = 75;

//                                         224, 75, 224, 149

//        OutSections S1 = new OutSections(0, 0, 0, 224,
//                                         74, 0, 74, 224);
//
//        OutSections S2 = new OutSections(75, 0, 75, 74,
//                                         149, 0, 149, 75);
//
//        OutSections S3 = new OutSections(75, 75, 75, 149,
//                                         149, 75, 149, 149);
//
//        OutSections S4 = new OutSections(75, 150, 75, 224,
//                                         149, 150, 149, 224);
//
//        OutSections S5 = new OutSections(150, 0, 150, 74,
//                                         224, 0, 224, 74);
//
        OutSections S6 = new OutSections(150, 75, 150, 149,
                                         224, 75, 224, 149);
//
//        OutSections S7 = new OutSections(150, 150, 150, 224,
//                                         224, 150, 224, 224);

        int pixelColor_1 = segmOut.getPixel(S6.topLeftx+25, S6.topLefty+25);
        // int pixelColor_2 = segmOut.getPixel(S6.topLeftx+50, S6.topLefty+25);
        // int pixelColor_3 = segmOut.getPixel(S6.topLeftx+25, S6.topLefty+50);
        // int pixelColor_4 = segmOut.getPixel(S6.topLeftx+50, S6.topLefty+50);

        // 1 2
        // 3 4

//        int red_1 = Color.red(pixelColor_1);
//        int green_1 = Color.green(pixelColor_1);
//        int blue_1 = Color.blue(pixelColor_1);
//        int red_2 = Color.red(pixelColor_2);
//        int green_2 = Color.green(pixelColor_2);
//        int blue_2 = Color.blue(pixelColor_2);
//        int red_3 = Color.red(pixelColor_3);
//        int green_3 = Color.green(pixelColor_3);
//        int blue_3 = Color.blue(pixelColor_3);
//        int red_4 = Color.red(pixelColor_4);
//        int green_4 = Color.green(pixelColor_4);
//        int blue_4 = Color.blue(pixelColor_4);
        String r_1, g_1, b_1, r_2, g_2, b_2;

//        r_1 = Integer.toString(red_1);
//        g_1 = Integer.toString(green_1);
//        b_1 = Integer.toString(blue_1);
//        r_2 = Integer.toString(red_2);
//        g_2 = Integer.toString(green_2);
//        b_2 = Integer.toString(blue_2);
//        r_3 = Integer.toString(red_3);
//        g_3 = Integer.toString(green_3);
//        b_3 = Integer.toString(blue_3);
//        r_4 = Integer.toString(red_4);
//        g_4 = Integer.toString(green_4);
//        b_4 = Integer.toString(blue_4);

//        Toast.makeText(getApplicationContext(), r_1 + " " + g_1 + " " + b_1, Toast.LENGTH_SHORT).show();

//        if(red_1 == 128 && green_1 == 64 && blue_1 == 128) {
//             point_count++;
//        }
//        if(red_2 == 128 && green_2 == 64 && blue_2 == 128) {
//            point_count++;
//        }
//        if(red_3 == 128 && green_3 == 64 && blue_3 == 128) {
//            point_count++;
//        }
//        if(red_4 == 128 && green_4 == 64 && blue_4 == 128) {
//            point_count++;
//        }

        int pixelColor1 = segmOut.getPixel(100, 150);
        int pixelColor2 = segmOut.getPixel(125, 150);

        int red_1 = Color.red(pixelColor1);
        int green_1 = Color.green(pixelColor1);
        int blue_1 = Color.blue(pixelColor1);
        int red_2 = Color.red(pixelColor2);
        int green_2 = Color.green(pixelColor2);
        int blue_2 = Color.blue(pixelColor2);
        r_1 = Integer.toString(red_1);
        g_1 = Integer.toString(green_1);
        b_1 = Integer.toString(blue_1);
        r_2 = Integer.toString(red_2);
        g_2 = Integer.toString(green_2);
        b_2 = Integer.toString(blue_2);

        Toast.makeText(getApplicationContext(), r_1 + " " + g_1 + " " + b_1 + " | " + r_2 + " " + g_2 + " " + b_2, Toast.LENGTH_SHORT).show();

        if(red_1 == 128 && green_1 == 64 && blue_1 == 128) {
             point_count++;
        }
        if(red_2 == 128 && green_2 == 64 && blue_2 == 128) {
             point_count++;
        }

        Log.d("OUT", Integer.toString(point_count));

        if(point_count > 0) {
            return 1;
        } else {
            return 2;
        }
    }

    private void voiceOutput(int voiceLine) {
        String lineToSpeak;
        switch(voiceLine) {
            case 1:
                lineToSpeak = "Clear.";
                break;
            case 2:
                lineToSpeak = "Clutter in front.";
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + "");
        }

        Toast.makeText(MainActivity.this,lineToSpeak,Toast.LENGTH_LONG).show();

        //speak
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mTTs.speak(lineToSpeak,TextToSpeech.QUEUE_FLUSH,null,null);
        } else {
            mTTs.speak(lineToSpeak, TextToSpeech.QUEUE_FLUSH, null);
        }

    }

    private void updateSegmPreview(Bitmap segmOutput) {
        Toast.makeText(MainActivity.this, "Updating Preview",Toast.LENGTH_LONG).show();
        mMiniView= findViewById(R.id.MiniView);
        mMiniView.setImageBitmap(segmOutput);
    }

}