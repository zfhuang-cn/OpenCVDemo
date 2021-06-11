package com.ant.idcard.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;

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
}