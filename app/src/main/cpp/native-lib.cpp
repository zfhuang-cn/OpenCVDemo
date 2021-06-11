#include <jni.h>
#include <opencv2/opencv.hpp>

#define DEFAULT_CARD_WIDTH 640
#define DEFAULT_CARD_HEIGHT 400
#define FIX_ID_CARD_SIZE Size(DEFAULT_CARD_WIDTH,DEFAULT_CARD_HEIGHT)

using namespace cv;
using namespace std;

extern "C" JNIEXPORT void JNICALL
Java_org_opencv_android_Utils_nBitmapToMat2(JNIEnv *env, jclass, jobject bitmap, jlong m_addr,
                                            jboolean needUnPremultiplyAlpha);
extern "C" JNIEXPORT void JNICALL
Java_org_opencv_android_Utils_nMatToBitmap(JNIEnv *env, jclass, jlong m_addr, jobject bitmap);

jobject createBitmap(JNIEnv *env, Mat srcData, jobject config) {
    int imgWidth = srcData.cols;
    int imgHeight = srcData.rows;

    jclass bmpCls = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmapMid = env->GetStaticMethodID(bmpCls, "createBitmap",
                                                       "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
    jobject jBmpObj = env->CallStaticObjectMethod(bmpCls, createBitmapMid, imgWidth, imgHeight,
                                                  config);
    Java_org_opencv_android_Utils_nMatToBitmap(env, 0, (jlong) &srcData, jBmpObj);
    return jBmpObj;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_ant_opencvdemo_jni_ImageProcess_getIdNumber(JNIEnv *env, jclass type, jobject src,
                                                     jobject config) {
    Mat src_img;
    Mat dst_img;
    //bitmap转为Mat格式数据
    Java_org_opencv_android_Utils_nBitmapToMat2(env, type, src, (jlong) &src_img, 0);

    Mat dst;
    //无损压缩
    resize(src_img, src_img, FIX_ID_CARD_SIZE);
    //灰度
    cvtColor(src_img, dst, COLOR_BGR2GRAY);
    //二値化
    threshold(dst, dst, 110, 255, CV_THRESH_BINARY);
    //发酵
    Mat erodeElement = getStructuringElement(MORPH_RECT, Size(20, 10));
    erode(dst, dst, erodeElement);

    vector<vector<Point>> contours;
    vector<Rect> rects;

    findContours(dst, contours, RETR_TREE, CHAIN_APPROX_SIMPLE, Point(0, 0));

    for (auto & contour : contours) {
        Rect rect = boundingRect(contour);
        rectangle(dst, rect, Scalar(0, 0, 255));
        //筛选图片
        if (rect.width > rect.height * 9) {
            rects.push_back(rect);
            rectangle(dst, rect, Scalar(0, 0, 255));
            dst_img = src_img(rect);
        }
    }

    //如果只找到一个矩形，就是目标图片
    if (rects.size() == 1) {
        Rect rect = rects.at(0);
        dst_img = src_img(rect);
    } else {
        int lowPoint = 0;
        Rect finalRect;
        //轮询所有的轮廓，并选择纵坐标最低的
        for (const Rect& rect : rects) {
            if (rect.tl().y > lowPoint) {
                lowPoint = rect.tl().y;
                finalRect = rect;
            }
        }
        rectangle(dst, finalRect, Scalar(255, 255, 0));
        dst_img = src_img(finalRect);
    }

    return createBitmap(env, dst_img, config);
}