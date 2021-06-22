package com.ant.idcard.jni;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;

import com.ant.idcard.R;
import com.ant.idcard.utils.BitmapUtil;
import com.ant.idcard.utils.TessUtil;

/**
 * 应用模块:
 * <p>
 * 类描述:
 * <p>
 *
 * @author: zfhuang
 */
public class IDCardHandler {

    private static final String ID_NUMBER_BITMAP = "id_number_bitmap";

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    public static native Bitmap getIdNumber(Bitmap src, Bitmap.Config config);

}