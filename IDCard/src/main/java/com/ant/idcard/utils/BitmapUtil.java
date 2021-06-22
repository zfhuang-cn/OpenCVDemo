package com.ant.idcard.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;

import com.blankj.utilcode.util.FileUtils;
import com.blankj.utilcode.util.PathUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

/**
 * 应用模块:
 * <p>
 * 类描述:
 * <p>
 *
 * @author: zfhuang
 * @date: 2021/6/8
 */
public class BitmapUtil {
    public static Bitmap imageToBitmap(Image image) {
        ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
        byte[] data = new byte[byteBuffer.remaining()];
        byteBuffer.get(data);
        image.close();
        return BitmapFactory.decodeByteArray(data, 0, data.length);
    }

    public static void save(Bitmap bitmap) {
        FileOutputStream outputStream = null;
        try {
            FileUtils.createOrExistsFile(PathUtils.getExternalPicturesPath());
            File file = new File(PathUtils.getExternalPicturesPath(),
                    System.currentTimeMillis() + ".jpg");
            outputStream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}