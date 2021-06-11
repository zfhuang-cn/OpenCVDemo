package com.ant.face;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.ant.face.R;
import com.ant.face.databinding.ActivityFaceBinding;

/**
 * 应用模块:
 * <p>
 * 类描述:
 * <p>
 *
 * @author: zfhuang
 * @date: 2021/6/8
 */
public class FaceActivity extends AppCompatActivity {

    private ActivityFaceBinding binding;
    private Camera2HelperFace mCamera2HelperFace;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFaceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mCamera2HelperFace = new Camera2HelperFace(this, binding.textureView);
        mCamera2HelperFace.setFaceDetectListener((faces, facesRect) -> binding.faceView.setFaces(facesRect));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCamera2HelperFace.releaseCamera();
        mCamera2HelperFace.releaseThread();
    }

}