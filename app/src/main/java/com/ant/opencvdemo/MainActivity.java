package com.ant.opencvdemo;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.ant.opencvdemo.face.FaceActivity;
import com.ant.opencvdemo.idcard.IDCardRecognitionActivity;
import com.blankj.utilcode.util.Utils;
import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.Logger;

import org.jetbrains.annotations.NotNull;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity {

    private static final int RC_CAMERA = 01010;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void onIdCardRecognitionClick(View view) {
        requireCameraPermission();
    }

    public void onFaceDetectionClicked(View view){
        startActivity(new Intent(this, FaceActivity.class));
    }

    @AfterPermissionGranted(RC_CAMERA)
    private void requireCameraPermission() {
        String[] perms = {Manifest.permission.CAMERA};
        if (EasyPermissions.hasPermissions(this, perms)) {
            // 已经有权限，进行相关操作
            startActivity(new Intent(this, IDCardRecognitionActivity.class));
        } else {
            // 没有权限，进行权限请求
            EasyPermissions.requestPermissions(this, getString(R.string.camera_rationale),
                    RC_CAMERA, perms);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull @NotNull String[] permissions, @NonNull @NotNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode,permissions,grantResults,this);
    }
}