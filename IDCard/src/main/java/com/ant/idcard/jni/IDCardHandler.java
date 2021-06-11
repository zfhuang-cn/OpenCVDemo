package com.ant.idcard.jni;

import android.graphics.Bitmap;

/**
 * 应用模块:
 * <p>
 * 类描述:
 * <p>
 *
 * @author: zfhuang
 */
public class IDCardHandler {
    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }
    public static native Bitmap getIdNumber(Bitmap src,Bitmap.Config config);
}