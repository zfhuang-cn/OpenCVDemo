package com.ant.opencvdemo.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import com.blankj.utilcode.util.AppUtils;
import com.blankj.utilcode.util.Utils;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 应用模块:
 * <p>
 * 类描述: 文字识别
 * <p>
 *
 * @author: zfhuang
 */
public class TessUtil {
    private static final String TAG = "TessUtil";

    TessBaseAPI tessBaseAPI;

    private static TessUtil sInstance;

    public static TessUtil getInstance() {
        if (sInstance == null) {
            synchronized (TessUtil.class) {
                if (sInstance == null) {
                    sInstance = new TessUtil();
                }
            }
        }
        return sInstance;
    }

    private void copyLanguagePackageToSDCard(Context context) {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            File file = new File(getLanguagePath(), "chi_sim.traineddata");
            outputStream = new FileOutputStream(file);
            //拷贝文件
            inputStream = context.getAssets().open("chi_sim.traineddata");
            byte[] buffer = new byte[1024];
            int length = inputStream.read(buffer);
            while (length > 1) {
                outputStream.write(buffer, 0, length);
                length = inputStream.read(buffer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.flush();
                    outputStream.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void init() {
        if (tessBaseAPI != null) {
            return;
        }
        copyLanguagePackageToSDCard(Utils.getApp());
        tessBaseAPI = new TessBaseAPI();
        tessBaseAPI.init(getLanguagePath(), "chi_sim");
        tessBaseAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
    }

    private String getLanguagePath() {
        File file = new File(Environment.getExternalStorageDirectory(), "tessdata");
        if (!file.exists()) {
            if (!file.mkdirs()) {
                Log.e(TAG, "copyLanguagePackageToSDCard: Failed to create folder.");
            }
        }
        return file.getAbsolutePath();
    }

    public String recognition(Bitmap bitmap) {
        if (tessBaseAPI == null) {
            init();
        }
        tessBaseAPI.setImage(bitmap);
        String result = tessBaseAPI.getUTF8Text();
        tessBaseAPI.clear();
        return result;
    }
}
