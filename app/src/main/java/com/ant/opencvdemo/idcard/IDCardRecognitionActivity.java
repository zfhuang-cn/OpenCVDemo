package com.ant.opencvdemo.idcard;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.ant.opencvdemo.R;
import com.ant.opencvdemo.databinding.ActivityIdCardRecognitionBinding;
import com.ant.opencvdemo.jni.ImageProcess;
import com.ant.opencvdemo.utils.TessUtil;
import com.orhanobut.logger.Logger;

/**
 * 应用模块: 身份证识别
 * <p>
 * 类描述:
 * <p>
 *
 * @author: zfhuang
 */
public class IDCardRecognitionActivity extends AppCompatActivity {

    private static final int PICK_PHOTO = 101;
    ActivityIdCardRecognitionBinding binding;
    private Camera2Helper mCamera2Helper;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityIdCardRecognitionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
//        mCamera2Helper = new Camera2Helper(this, binding.textureView);
//        mCamera2Helper.setIDRecognitionListener((bitmap, idNumber) -> {
//            binding.ivIdNumber.setImageBitmap(bitmap);
//            binding.tvIdNumber.setText(idNumber);
//            Logger.d( " ID number : %s",idNumber);
//        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.id_card, menu);
        return true;
    }

    public void onChoosePhotoClicked(MenuItem view) {
        //动态申请获取访问 读写磁盘的权限
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
        } else {
            //打开相册
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_PHOTO); // 打开相册
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_PHOTO && resultCode == RESULT_OK) {
            handleImage(data);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCamera2Helper != null) {
            mCamera2Helper.releaseCamera();
            mCamera2Helper.releaseThread();
        }
    }

    private void handleImage(Intent data) {
        String imagePath = null;
        Uri uri = data.getData();
        if (DocumentsContract.isDocumentUri(this, uri)) {
            // 如果是document类型的Uri，则通过document id处理
            String docId = DocumentsContract.getDocumentId(uri);
            if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                String id = docId.split(":")[1];
                // 解析出数字格式的id
                String selection = MediaStore.Images.Media._ID + "=" + id;
                imagePath = getImagePath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection);
            } else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
                Uri contentUri = ContentUris.withAppendedId(Uri.parse("content: " +
                        "//downloads/public_downloads"), Long.valueOf(docId));
                imagePath = getImagePath(contentUri, null);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            // 如果是content类型的Uri，则使用普通方式处理
            imagePath = getImagePath(uri, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            // 如果是file类型的Uri，直接获取图片路径即可
            imagePath = uri.getPath();
        }
        displayImage(imagePath);
        // 识别图片中的身份证号
        recognitionIdNumber(imagePath);
    }

    private void displayImage(String imagePath) {
        Logger.d("displayImage: ");
    }

    private String getImagePath(Uri uri, String selection) {
        String path = null;
        // 通过Uri和selection来获取真实的图片路径
        Cursor cursor = getContentResolver().query(uri, null, selection, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
            }
            cursor.close();
        }
        return path;
    }

    private void recognitionIdNumber(String imagePath) {
        if (imagePath != null) {
            new Thread(() -> {
                try {
                    Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                    //获取身份证号图片
                    Bitmap bitmapResult = ImageProcess.getIdNumber(bitmap, Bitmap.Config.ARGB_8888);
                    //识别文字
                    String strResult = TessUtil.getInstance().recognition(bitmapResult);
                    runOnUiThread(() -> {
                        binding.tvIdNumber.setText(strResult);
                        binding.ivIdNumber.setImageBitmap(bitmapResult);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        } else {
            Toast.makeText(this, "获取相册图片失败", Toast.LENGTH_SHORT).show();
        }
    }
}