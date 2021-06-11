package com.ant.face;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
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
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.ant.face.view.AutoFitTextureView;
import com.orhanobut.logger.Logger;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 应用模块:
 * <p>
 * 类描述:
 * <p>
 *
 * @author: zfhuang
 * @date: 2021/6/7
 */
public class Camera2HelperFace {

    private static final String TAG = "Camera2Helper";

    final static int PREVIEW_WIDTH = 1080;                             //预览的宽度
    final static int PREVIEW_HEIGHT = 1440;                            //预览的高度
    final static int SAVE_WIDTH = 720;                                 //保存图片的宽度
    final static int SAVE_HEIGHT = 1280;                               //保存图片的高度

    private Activity mActivity;
    private AutoFitTextureView mTextureView;

    private CameraManager mCameraManager;
    private ImageReader mImageReader;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSession;

    private String mCameraId;
    private CameraCharacteristics mCameraCharacteristics;

    private int mCameraSensorOrientation;                                           //摄像头方向
    private int mCameraFacing = CameraCharacteristics.LENS_FACING_FRONT;            //默认使用前置摄像头
    private int mDisplayRotation;                                                   //手机方向
    private int mFaceDetectMode = CaptureResult.STATISTICS_FACE_DETECT_MODE_OFF;    //人脸检测模式

    private boolean canExchangeCamera = false;                                      //是否可以切换摄像头
    private boolean openFaceDetect = true;                                          //是否开启人脸检测
    private final Matrix mFaceDetectMatrix = new Matrix();                          //人脸检测坐标转换矩阵
    private final List<RectF> mFacesRect = new ArrayList<>();                       //保存人脸坐标信息
    private FaceDetectListener mFaceDetectListener;                                 //人脸检测回调

    private Handler mCameraHandler;
    private final HandlerThread handlerThread = new HandlerThread("CameraThread");

    private Size mPreviewSize = new Size(PREVIEW_WIDTH, PREVIEW_HEIGHT);    //预览大小
    private Size mSavePicSize = new Size(SAVE_WIDTH, SAVE_HEIGHT);          //保存图片大小

    public Camera2HelperFace(@NotNull Activity activity, @NotNull AutoFitTextureView textureView) {
        this.mActivity = activity;
        this.mTextureView = textureView;

        handlerThread.start();
        mCameraHandler = new Handler(handlerThread.getLooper());

        mDisplayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();

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
                Log.d(TAG, "没有可用相机");
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

            Size[] savePicSize = configurationMap.getOutputSizes(ImageFormat.JPEG);        //保存照片尺寸
            Size[] previewSize = configurationMap.getOutputSizes(SurfaceTexture.class);    //预览尺寸

            boolean exchange = exchangeWidthAndHeight(mDisplayRotation, mCameraSensorOrientation);

            mSavePicSize = getBestSize(
                    exchange ? mSavePicSize.getHeight() : mSavePicSize.getWidth(),
                    exchange ? mSavePicSize.getWidth() : mSavePicSize.getHeight(),
                    exchange ? mSavePicSize.getHeight() : mSavePicSize.getWidth(),
                    exchange ? mSavePicSize.getWidth() : mSavePicSize.getHeight(),
                    Arrays.asList(savePicSize));

            mPreviewSize = getBestSize(
                    exchange ? mPreviewSize.getHeight() : mPreviewSize.getWidth(),
                    exchange ? mPreviewSize.getWidth() : mPreviewSize.getHeight(),
                    exchange ? mTextureView.getHeight() : mTextureView.getWidth(),
                    exchange ? mTextureView.getWidth() : mTextureView.getHeight(),
                    Arrays.asList(previewSize));

            mTextureView.getSurfaceTexture().setDefaultBufferSize(mPreviewSize.getWidth(),
                    mPreviewSize.getHeight());

            Logger.d("预览最优尺寸 ：%d * %d , 比例 %f",
                    mPreviewSize.getWidth() ,mPreviewSize.getHeight(),
                    (float) mPreviewSize.getWidth() / (float) mPreviewSize.getHeight());
            Logger.d("保存图片最优尺寸 ：%d * %d , 比例 %f",
                    mSavePicSize.getWidth(), mSavePicSize.getHeight(),
                    (float) mSavePicSize.getWidth() / (float) mSavePicSize.getHeight());

            //根据预览的尺寸大小调整TextureView的大小，保证画面不被拉伸
            int orientation = mActivity.getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE)
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            else
                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());

            mImageReader = ImageReader.newInstance(mSavePicSize.getWidth(),
                    mSavePicSize.getHeight(), ImageFormat.JPEG, 1);
            mImageReader.setOnImageAvailableListener(imageReader -> {
                //图片保存;
            }, mCameraHandler);

            if (openFaceDetect) {
                initFaceDetect();
            }
            openCamera();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initFaceDetect() {
        int faceDetectCount =
                mCameraCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT);    //同时检测到人脸的数量
        int[] faceDetectModes =
                mCameraCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);  //人脸检测的模式

        mFaceDetectMode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF;
        for (int mode : faceDetectModes) {
            if (mode == CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL ||
                    mode == CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE) {
                mFaceDetectMode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL;
                break;
            }
        }

        if (mFaceDetectMode == CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF) {
            Logger.d("相机硬件不支持人脸检测");
            return;
        }

        Rect activeArraySizeRect =
                mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        //获取成像区域
        float scaledWidth = mPreviewSize.getWidth() / (float) activeArraySizeRect.width();
        float scaledHeight = mPreviewSize.getHeight() / (float) activeArraySizeRect.height();
        boolean mirror = mCameraFacing == CameraCharacteristics.LENS_FACING_FRONT;

        mFaceDetectMatrix.setRotate((float) mCameraSensorOrientation);
        mFaceDetectMatrix.postScale(mirror ? -scaledWidth : scaledWidth, scaledHeight);
        if (exchangeWidthAndHeight(mDisplayRotation, mCameraSensorOrientation))
            mFaceDetectMatrix.postTranslate(mPreviewSize.getHeight(), mPreviewSize.getWidth());

//        for (int mode : faceDetectModes) {
//            Logger.d("支持的人脸检测模式 %d", mode);
//        }
//        Logger.d("同时检测到人脸的数量 %d", faceDetectCount);
    }

    /**
     * 打开相机
     */
    private void openCamera() {
        if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Logger.e("没有相机权限！");
            return;
        }
        try {
            mCameraManager.openCamera(mCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    Logger.d(TAG, "onOpened");
                    mCameraDevice = camera;
                    try {
                        createCaptureSession(camera);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    Logger.d(TAG, "onDisconnected");
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Logger.d(TAG, "打开相机失败！" + error);
                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            Logger.e(TAG, "openCamera: ", e);
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
        if (openFaceDetect && mFaceDetectMode != CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF)
            captureRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                    CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_SIMPLE);//人脸检测

        // 为相机预览，创建一个CameraCaptureSession对象
        cameraDevice.createCaptureSession(Arrays.asList(surface,
                mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
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
                    if (openFaceDetect && mFaceDetectMode != CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF)
                        handleFaces(result);
                    canExchangeCamera = true;
                }

                public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                                            CaptureFailure failure) {
                    super.onCaptureFailed(session, request, failure);
                    Log.d(TAG, "onCaptureFailed");
                }
            };

    /**
     * 处理人脸信息
     */
    private void handleFaces(TotalCaptureResult result) {
        Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
        mFacesRect.clear();

        for (Face face : faces) {
            Rect bounds = face.getBounds();
            RectF rawFaceRect = new RectF(bounds.left, bounds.top, bounds.right, bounds.bottom);
            mFaceDetectMatrix.mapRect(rawFaceRect);

            RectF resultFaceRect = mCameraFacing == CaptureRequest.LENS_FACING_FRONT ?
                    rawFaceRect
                    :
                    new RectF(rawFaceRect.left, rawFaceRect.top - mPreviewSize.getWidth(),
                            rawFaceRect.right, rawFaceRect.bottom - mPreviewSize.getWidth());

            mFacesRect.add(resultFaceRect);

            Logger.d("原始人脸位置: %d * %d   %d %d %d %d   分数: %d", bounds.width(), bounds.height(),
                    bounds.left, bounds.top, bounds.right, bounds.bottom, face.getScore());
            Logger.d("转换后人脸位置: %f * %f   %f %f %f %f   分数: %d", resultFaceRect.width(),
                    resultFaceRect.height(), resultFaceRect.left, resultFaceRect.top,
                    resultFaceRect.right, resultFaceRect.bottom, face.getScore());
        }

        if (mFaceDetectListener != null) {
            mActivity.runOnUiThread(() -> mFaceDetectListener.onFaceDetect(faces, mFacesRect));
        }
        Logger.d("onCaptureCompleted  检测到 %d 张人脸", faces.length);
    }

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
                             List<Size> sizeList) {
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
            Logger.d("系统支持的尺寸: %d * %d ,  比例 ：%f",
                    size.getWidth(), size.getHeight(), size.getWidth() / (float) size.getHeight());
        }

        Logger.d("最大尺寸 ：%d * %d, 比例 ：%f", maxWidth, maxHeight, targetWidth / (float) targetHeight);
        Logger.d(TAG, "目标尺寸 ：%d * %d, 比例 ：%f",
                targetWidth, targetHeight, targetWidth / (float) targetHeight);

        //选择bigEnough中最小的值  或 notBigEnough中最大的值
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            return sizeList.get(0);
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
                Logger.d("Display rotation is invalid: %d", displayRotation);
        }

        Logger.d("屏幕方向 %d", displayRotation);
        Logger.d("相机方向 %d", sensorOrientation);
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
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        canExchangeCamera = false;
    }

    public void releaseThread() {
        try {
            handlerThread.quitSafely();
            handlerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
        RectF bufferRect = new RectF(0f, 0f, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            int scale = Math.max(viewHeight / mPreviewSize.getHeight(),
                    viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate((90 * (rotation - 2)), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
        Logger.d("configureTransform %d %d", viewWidth, viewHeight);
    }

    public void setFaceDetectListener(FaceDetectListener faceDetectListener) {
        this.mFaceDetectListener = faceDetectListener;
    }

    interface FaceDetectListener {
        void onFaceDetect(Face[] faces, List<RectF> facesRect);
    }
    class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size size1, Size size2) {
            return Long.signum((long) size1.getWidth() * (long) size1.getHeight() - (long) size2.getWidth() * (long) size2.getHeight());
        }
    }
}