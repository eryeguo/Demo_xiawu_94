package com.example.beijing01.demo_xiawu_94;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.nanchen.compresshelper.CompressHelper;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    private ImageView imageView;

    private static final String TAG = "MainActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = (ImageView) findViewById(R.id.iv_image);
        CameraActivity.toCameraActivity(this, CameraActivity.TYPE_IDCARD_FRONT);
    }
    /**
     * 身份证正面
     */
    public void frontIdCard(View view) {

    }

    /**
     * 身份证反面
     */
    public void backIdCard(View view) {
        CameraActivity.toCameraActivity(this, CameraActivity.TYPE_IDCARD_BACK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CameraActivity.REQUEST_CODE && resultCode == CameraActivity.RESULT_CODE) {
            //获取图片路径，显示图片
            final String path = CameraActivity.getImagePath(data);
            if (!TextUtils.isEmpty(path)) {
                File file = new File(path);
                String yourFileName = System.currentTimeMillis() + "";
                File newFile = new CompressHelper.Builder(this)
                        .setMaxWidth(720)  // 默认最大宽度为720
                        .setMaxHeight(960) // 默认最大高度为960
                        .setQuality(80)    // 默认压缩质量为80
                        .setFileName(yourFileName) // 设置你需要修改的文件名
                        .setCompressFormat(Bitmap.CompressFormat.JPEG) // 设置默认压缩为jpg格式
                        .setDestinationDirectoryPath(Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_PICTURES).getAbsolutePath())
                        .build()
                        .compressToFile(file);
                imageView.setImageBitmap(BitmapFactory.decodeFile(newFile.getPath()));
            }
        }
    }
}
