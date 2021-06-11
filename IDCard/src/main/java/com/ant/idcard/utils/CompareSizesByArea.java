package com.ant.idcard.utils;


import android.util.Size;

import java.util.Comparator;

/**
 * 应用模块:
 * <p>
 * 类描述:
 * <p>
 *
 * @author: zfhuang
 */
public class CompareSizesByArea implements Comparator<Size> {
    @Override
    public int compare(Size size1, Size size2) {
        return Long.signum((long) size1.getWidth() * (long) size1.getHeight() - (long) size2.getWidth() * (long) size2.getHeight());
    }
}