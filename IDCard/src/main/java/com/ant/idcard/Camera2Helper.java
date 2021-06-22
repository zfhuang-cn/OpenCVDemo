package com.ant.idcard;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.ant.idcard.jni.IDCardHandler;
import com.ant.idcard.utils.BitmapUtil;
import com.ant.idcard.utils.CompareSizesByArea;
import com.ant.idcard.utils.TessUtil;
import com.blankj.utilcode.util.FileUtils;
import com.orhanobut.logger.Logger;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 应用模块:
 * <p>
 * 类描述:
 * <p>
 *
 * @author: zfhuang
 */
public class Camera2Helper {

    private static final String TAG = "Camera2Helper";

    final static int PREVIEW_WIDTH = 1080;                             //预览的宽度
    final static int PREVIEW_HEIGHT = 540;                            //预览的高度
    private Activity mActivity;
    private TextureView mTextureView;

    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSession;

    private String mCameraId;
    private CameraCharacteristics mCameraCharacteristics;

    private int mCameraSensorOrientation;                                       //摄像头方向
    private int mCameraFacing = CameraCharacteristics.LENS_FACING_BACK;         //默认使用后置摄像头
    private int mDisplayRotation;                                               //手机方向

    private boolean canExchangeCamera = false;                                  //是否可以切换摄像头

    private final Handler mCameraHandler;
    private final HandlerThread handlerThread = new HandlerThread("CameraThread");

    private Size mPreviewSize = new Size(PREVIEW_WIDTH, PREVIEW_HEIGHT);    //预览大小

    private IDRecognitionListener mIDRecognitionListener;

    private State state = State.PREVIEW;

    private enum State {
        PREVIEW,
        SUCCESS,
        HANDLING
    }

    interface IDRecognitionListener {
        void onIDCardDetect(Bitmap srcBitmap, Bitmap idNumberBitmap, String idNumber);
    }

    public Camera2Helper(@NotNull Activity activity, @NotNull TextureView textureView) {
        this.mActivity = activity;
        this.mTextureView = textureView;

        mDisplayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();

        handlerThread.start();
        mCameraHandler = new Handler(handlerThread.getLooper());
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width,
                                                  int height) {
                configureTransform(width, height);
                initCameraInfo();
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width,
                                                    int height) {
                configureTransform(width, height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                releaseCamera();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
            }
        });
    }

    private void initCameraInfo() {
        try {
            mCameraManager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
            String[] cameraIdList = mCameraManager.getCameraIdList();
            if (cameraIdList == null || cameraIdList.length == 0) {
                Log.d(TAG, "initCameraInfo: ");
                return;
            }
            for (String id : cameraIdList) {
                CameraCharacteristics cameraCharacteristics =
                        mCameraManager.getCameraCharacteristics(id);
                int facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == mCameraFacing) {
                    mCameraId = id;
                    mCameraCharacteristics = cameraCharacteristics;
                }
                Log.d(TAG, "设备中的摄像头" + id);
            }

            int supportLevel =
                    mCameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            if (supportLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                Log.d(TAG, "相机硬件不支持新特性");
            }

            //获取摄像头方向
            mCameraSensorOrientation =
                    mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            //获取StreamConfigurationMap，它是管理摄像头支持的所有输出格式和尺寸
            StreamConfigurationMap configurationMap =
                    mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            Size[] previewSize = configurationMap.getOutputSizes(SurfaceTexture.class);     //预览尺寸

            boolean exchange = exchangeWidthAndHeight(mDisplayRotation, mCameraSensorOrientation);

            mPreviewSize = getBestSize(
                    exchange ? mPreviewSize.getHeight() : mPreviewSize.getWidth(),
                    exchange ? mPreviewSize.getWidth() : mPreviewSize.getHeight(),
                    exchange ? mTextureView.getHeight() : mTextureView.getWidth(),
                    exchange ? mTextureView.getWidth() : mTextureView.getHeight(),
                    previewSize);

            mTextureView.getSurfaceTexture().setDefaultBufferSize(mPreviewSize.getWidth(),
                    mPreviewSize.getHeight());

            Logger.d("预览最优尺寸 ：%d * %d , 比例 : %f",
                    mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                    (float) mPreviewSize.getWidth() / (float) mPreviewSize.getHeight());

            openCamera();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleIDCard() {
        state = State.HANDLING;
        new Thread(() -> {
            try {
                //获取身份证号图片
                Bitmap bitmap = mTextureView.getBitmap(mPreviewSize.getWidth(),mPreviewSize.getHeight() * 2);
                if (bitmap == null) {
                    Logger.d("bitmap is null.");
                    state = State.PREVIEW;
                    return;
                }
                Bitmap resultBitmap = IDCardHandler.getIdNumber(bitmap, Bitmap.Config.ARGB_8888);
                if (resultBitmap == null) {
                    state = State.PREVIEW;
                    return;
                }
                //识别文字
                String resultStr = TessUtil.getInstance().recognition(resultBitmap);
                Logger.d("handleIDCard: id number = %s", resultStr);
                if (!TextUtils.isEmpty(resultStr)) {
                    mActivity.runOnUiThread(() -> {
                        if (mIDRecognitionListener != null) {
                            mIDRecognitionListener.onIDCardDetect(bitmap, resultBitmap, resultStr);
                        }
                    });
                    state = State.SUCCESS;
                }
            } catch (Exception e) {
                Logger.e(e, "handleIDCard: ");
                state = State.PREVIEW;
            }
        }).start();
    }

    /**
     * 打开相机
     */
    private void openCamera() {
        if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "没有相机权限！");
            return;
        }
        try {
            mCameraManager.openCamera(mCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    Log.d(TAG, "onOpened");
                    mCameraDevice = camera;
                    try {
                        createCaptureSession(camera);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    Log.d(TAG, "onDisconnected");

                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.d(TAG, "onError $error");
                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "openCamera: ", e);
        }
    }

    /**
     * 创建预览会话
     */
    private void createCaptureSession(CameraDevice cameraDevice) throws CameraAccessException {

        CaptureRequest.Builder captureRequestBuilder =
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        Surface surface = new Surface(mTextureView.getSurfaceTexture());
        captureRequestBuilder.addTarget(surface); // 将CaptureRequest的构建器与Surface对象绑定在一起
        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);     // 闪光灯
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);// 自动对焦

        // 为相机预览，创建一个CameraCaptureSession对象
        cameraDevice.createCaptureSession(Arrays.asList(surface),
                new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                mCameraCaptureSession = session;
                try {
                    session.setRepeatingRequest(captureRequestBuilder.build(), mCaptureCallBack,
                            mCameraHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                Logger.e(TAG, "开启预览会话失败 ");
            }
        }, mCameraHandler);
    }

    private final CameraCaptureSession.CaptureCallback mCaptureCallBack =
            new CameraCaptureSession.CaptureCallback() {

                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                               TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    // //在聚焦完成后进行自动捕捉图片
                    if (result.get(CaptureResult.CONTROL_AF_STATE) == CaptureRequest.CONTROL_AF_STATE_PASSIVE_FOCUSED && state == State.PREVIEW) {
                        handleIDCard();
                    }
                    canExchangeCamera = true;
                }

                public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                                            CaptureFailure failure) {
                    super.onCaptureFailed(session, request, failure);
                    Log.d(TAG, "onCaptureFailed");
                }
            };

    /**
     * 切换摄像头
     */
    public void exchangeCamera() {
        if (mCameraDevice == null || !canExchangeCamera) return;

        mCameraFacing = mCameraFacing == CameraCharacteristics.LENS_FACING_FRONT ?
                CameraCharacteristics.LENS_FACING_BACK :
                CameraCharacteristics.LENS_FACING_FRONT;

        mPreviewSize = new Size(PREVIEW_WIDTH, PREVIEW_HEIGHT);     //重置预览大小
        releaseCamera();
        initCameraInfo();
    }

    /**
     * 根据提供的参数值返回与指定宽高相等或最接近的尺寸
     *
     * @param targetWidth  目标宽度
     * @param targetHeight 目标高度
     * @param maxWidth     最大宽度(即TextureView的宽度)
     * @param maxHeight    最大高度(即TextureView的高度)
     * @param sizeList     支持的Size列表
     * @return 返回与指定宽高相等或最接近的尺寸
     */
    private Size getBestSize(int targetWidth, int targetHeight, int maxWidth, int maxHeight,
                             Size[] sizeList) {
        List<Size> bigEnough = new ArrayList<>();     //比指定宽高大的Size列表
        List<Size> notBigEnough = new ArrayList<>();  //比指定宽高小的Size列表

        for (Size size : sizeList) {

            //宽<=最大宽度  &&  高<=最大高度  &&  宽高比 == 目标值宽高比
            if (size.getWidth() <= maxWidth && size.getHeight() <= maxHeight
                    && size.getWidth() == size.getHeight() * targetWidth / targetHeight) {

                if (size.getWidth() >= targetWidth && size.getHeight() >= targetHeight)
                    bigEnough.add(size);
                else
                    notBigEnough.add(size);
            }
        }

        //选择bigEnough中最小的值  或 notBigEnough中最大的值
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            return sizeList[0];
        }
    }

    /**
     * 根据提供的屏幕方向 [displayRotation] 和相机方向 [sensorOrientation] 返回是否需要交换宽高
     */
    private boolean exchangeWidthAndHeight(int displayRotation, int sensorOrientation) {
        boolean exchange = false;
        switch (displayRotation) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    exchange = true;
                }
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                if (sensorOrientation == 0 || sensorOrientation == 180) {
                    exchange = true;
                }
                break;
            default:
                Logger.d("Display rotation is invalid: $displayRotation");
        }

        Logger.d("屏幕方向  %d", displayRotation);
        Logger.d("相机方向  %d", sensorOrientation);
        return exchange;
    }

    public void releaseCamera() {
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        canExchangeCamera = false;
    }

    public void setIDRecognitionListener(IDRecognitionListener listener) {
        this.mIDRecognitionListener = listener;
    }

    public void releaseThread() {
        handlerThread.quitSafely();
    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0f, 0f, viewWidth, viewHeight);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        matrix.postScale(1, 1.8f, centerX, centerY);

        mTextureView.setTransform(matrix);
        Logger.d("configureTransform %d %d rotation = %d", viewWidth, viewHeight, rotation);
    }
}