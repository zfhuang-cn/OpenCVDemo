package com.ant.opencvdemo;

import android.app.Application;

import androidx.annotation.Nullable;

import com.blankj.utilcode.util.Utils;
import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.Logger;

/**
 * 应用模块:
 * <p>
 * 类描述:
 * <p>
 *
 * @author: zfhuang
 * @date: 2021/6/8
 */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 初始化日志
        Logger.addLogAdapter(new AndroidLogAdapter() {
            @Override
            public boolean isLoggable(int priority, @Nullable String tag) {
                return BuildConfig.DEBUG;
            }
        });
        Utils.init(this);
    }
}