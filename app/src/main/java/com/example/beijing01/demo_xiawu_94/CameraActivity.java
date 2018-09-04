package com.example.beijing01.demo_xiawu_94;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class CameraActivity extends Activity implements View.OnClickListener {

    public final static int TYPE_IDCARD_FRONT = 1;//身份证正面
    public final static int TYPE_IDCARD_BACK = 2;//身份证反面
    public final static int REQUEST_CODE = 0X11;//请求码
    public final static int RESULT_CODE = 0X12;//结果码
    public final static int PERMISSION_CODE_FIRST = 0x13;//权限请求码
    public final static String TAKE_TYPE = "take_type";//拍摄类型标记
    public final static String IMAGE_PATH = "image_path";//图片路径标记
    public static int mType;//拍摄类型
    public static Activity mActivity;
    private boolean isToast = true;//是否弹吐司，为了保证for循环只弹一次

    private CropImageView mCropImageView;
    private Bitmap mCropBitmap;
    private CameraPreview mCameraPreview;
    private View mLlCameraCropContainer;
    private ImageView mIvCameraCrop;
    private ImageView mIvCameraFlash;
    private View mLlCameraOption;
    private View mLlCameraResult;
    private TextView mViewCameraCropBottom;

    /**
     * 跳转到拍照界面
     *
     * @param activity
     * @param type     拍摄类型
     */
    public static void toCameraActivity(Activity activity, int type) {
        Intent intent = new Intent(activity, CameraActivity.class);
        intent.putExtra(TAKE_TYPE, type);
        activity.startActivityForResult(intent, REQUEST_CODE);
    }

    /**
     * 获取图片路径
     *
     * @param data
     * @return
     */
    public static String getImagePath(Intent data) {
        if (data != null) {
            return data.getStringExtra(IMAGE_PATH);
        }
        return "";
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*动态请求需要的权限*/
        boolean checkPermissionFirst = checkPermissionFirst(this, PERMISSION_CODE_FIRST,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA});
        if (checkPermissionFirst) {
            init();
        }
    }

    /**
     * 处理请求权限的响应
     *
     * @param requestCode  请求码
     * @param permissions  权限数组
     * @param grantResults 请求权限结果数组
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean isPermissions = true;
        for (int i = 0; i < permissions.length; i++) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                isPermissions = false;
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) { //用户选择了"不再询问"
                    if (isToast) {
                        Toast.makeText(this, "请手动打开该应用需要的权限", Toast.LENGTH_SHORT).show();
                        isToast = false;
                    }
                }
            }
        }
        isToast = true;
        if (isPermissions) {
            Log.d("onRequestPermission", "onRequestPermissionsResult: " + "允许所有权限");
            init();
        } else {
            Log.d("onRequestPermission", "onRequestPermissionsResult: " + "有权限不允许");
            finish();
        }
    }

    private void init() {
        setContentView(R.layout.activity_camera);
        mType = getIntent().getIntExtra(TAKE_TYPE, 0);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        initView();
        initListener();
    }

    private void initView() {
        mCameraPreview = (CameraPreview) findViewById(R.id.camera_preview);
        mLlCameraCropContainer = findViewById(R.id.ll_camera_crop_container);
        mIvCameraCrop = (ImageView) findViewById(R.id.iv_camera_crop);
        mIvCameraFlash = (ImageView) findViewById(R.id.iv_camera_flash);
        mLlCameraOption = findViewById(R.id.ll_camera_option);
        mLlCameraResult = findViewById(R.id.ll_camera_result);
        mCropImageView = findViewById(R.id.crop_image_view);
        mViewCameraCropBottom = (TextView) findViewById(R.id.view_camera_crop_bottom);

        //获取屏幕最小边，设置为cameraPreview较窄的一边
        float screenMinSize = Math.min(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
        //根据screenMinSize，计算出cameraPreview的较宽的一边，长宽比为标准的16:9
        float maxSize = screenMinSize / 9.0f * 16.0f;
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams((int) maxSize, (int) screenMinSize);
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        mCameraPreview.setLayoutParams(layoutParams);

        float height = (int) (screenMinSize * 0.75);
        float width = (int) (height * 75.0f / 47.0f);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams((int) width, ViewGroup.LayoutParams.MATCH_PARENT);
        LinearLayout.LayoutParams cropParams = new LinearLayout.LayoutParams((int) width, (int) height);
        mLlCameraCropContainer.setLayoutParams(containerParams);
        mIvCameraCrop.setLayoutParams(cropParams);

        switch (mType) {
            case TYPE_IDCARD_FRONT:
                break;
            case TYPE_IDCARD_BACK:
                break;
        }

        /*增加0.5秒过渡界面，解决个别手机首次申请权限导致预览界面启动慢的问题*/
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mCameraPreview.setVisibility(View.VISIBLE);
                    }
                });
            }
        }, 500);
    }

    private void initListener() {
        mCameraPreview.setOnClickListener(this);
        mIvCameraFlash.setOnClickListener(this);
        findViewById(R.id.iv_camera_close).setOnClickListener(this);
        findViewById(R.id.iv_camera_take).setOnClickListener(this);
        findViewById(R.id.iv_camera_result_ok).setOnClickListener(this);
        findViewById(R.id.iv_camera_result_cancel).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.camera_preview) {
            mCameraPreview.focus();
        } else if (id == R.id.iv_camera_close) {
            finish();
        } else if (id == R.id.iv_camera_take) {
            takePhoto();
        } else if (id == R.id.iv_camera_flash) {
            boolean isFlashOn = mCameraPreview.switchFlashLight();
            mIvCameraFlash.setImageResource(isFlashOn ? R.mipmap.camera_flash_on : R.mipmap.camera_flash_off);
        } else if (id == R.id.iv_camera_result_ok) {
            confirm();
        } else if (id == R.id.iv_camera_result_cancel) {
            mCameraPreview.setEnabled(true);
            mCameraPreview.startPreview();
            mIvCameraFlash.setImageResource(R.mipmap.camera_flash_off);
            setTakePhotoLayout();
        }
    }

    /**
     * 拍照
     */
    private void takePhoto() {
        mCameraPreview.setEnabled(false);
        mCameraPreview.takePhoto(new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(final byte[] data, Camera camera) {
                camera.stopPreview();
                //子线程处理图片，防止ANR
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);

                        /*计算裁剪位置*/
                        float left, top, right, bottom;
                        left = ((float) mLlCameraCropContainer.getLeft() - (float) mCameraPreview.getLeft()) / (float) mCameraPreview.getWidth();
                        top = (float) mIvCameraCrop.getTop() / (float) mCameraPreview.getHeight();
                        right = (float) mLlCameraCropContainer.getRight() / (float) mCameraPreview.getWidth();
                        bottom = (float) mIvCameraCrop.getBottom() / (float) mCameraPreview.getHeight();

                        /*自动裁剪*/
                        mCropBitmap = Bitmap.createBitmap(bitmap,
                                (int) (left * (float) bitmap.getWidth()),
                                (int) (top * (float) bitmap.getHeight()),
                                (int) ((right - left) * (float) bitmap.getWidth()),
                                (int) ((bottom - top) * (float) bitmap.getHeight()));

                        /*手动裁剪*/
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //将裁剪区域设置成与扫描框一样大
                                mCropImageView.setLayoutParams(new LinearLayout.LayoutParams(mIvCameraCrop.getWidth(), mIvCameraCrop.getHeight()));
                                setCropLayout();
                                mCropImageView.setImageBitmap(mCropBitmap);
                            }
                        });
                    }
                }).start();
            }
        });
    }

    /**
     * 设置裁剪布局
     */
    private void setCropLayout() {
        mIvCameraCrop.setVisibility(View.GONE);
        mCameraPreview.setVisibility(View.GONE);
        mLlCameraOption.setVisibility(View.GONE);
        mCropImageView.setVisibility(View.VISIBLE);
        mLlCameraResult.setVisibility(View.VISIBLE);
        mViewCameraCropBottom.setText("");
    }

    /**
     * 设置拍照布局
     */
    private void setTakePhotoLayout() {
        mIvCameraCrop.setVisibility(View.VISIBLE);
        mCameraPreview.setVisibility(View.VISIBLE);
        mLlCameraOption.setVisibility(View.VISIBLE);
        mCropImageView.setVisibility(View.GONE);
        mLlCameraResult.setVisibility(View.GONE);
        mViewCameraCropBottom.setText("触摸屏幕对焦");

        mCameraPreview.focus();
    }

    /**
     * 点击确认，返回图片路径
     */
    private void confirm() {
        /*裁剪图片*/
        mCropImageView.crop(new CropOverlayView.CropListener() {
            @Override
            public void onFinish(Bitmap bitmap) {
                if (bitmap == null) {
                    Toast.makeText(getApplicationContext(), "裁剪失败", Toast.LENGTH_SHORT).show();
                    finish();
                }

                /*保存图片到sdcard并返回图片路径*/
                if (FileUtils.createOrExistsDir(Constant.DIR_ROOT)) {
                    StringBuffer buffer = new StringBuffer();
                    String imagePath = "";
                    if (mType == TYPE_IDCARD_FRONT) {
                        imagePath = buffer.append(Constant.DIR_ROOT).append(Constant.APP_NAME).append(".").append("idCardFrontCrop.jpg").toString();
                    } else if (mType == TYPE_IDCARD_BACK) {
                        imagePath = buffer.append(Constant.DIR_ROOT).append(Constant.APP_NAME).append(".").append("idCardBackCrop.jpg").toString();
                    }

                    if (ImageUtils.save(bitmap, imagePath, Bitmap.CompressFormat.JPEG)) {
                        Intent intent = new Intent();
                        intent.putExtra(CameraActivity.IMAGE_PATH, imagePath);
                        setResult(RESULT_CODE, intent);
                        finish();
                    }
                }
            }
        }, true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mCameraPreview != null) {
            mCameraPreview.onStart();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mCameraPreview != null) {
            mCameraPreview.onStop();
        }
    }

    public static class Constant {
        public static final String APP_NAME = "WildmaIDCardCamera";//app名称
        public static final String BASE_DIR = APP_NAME + File.separator;//WildmaIDCardCamera/
        public static final String DIR_ROOT = FileUtils.getRootPath() + File.separator + Constant.BASE_DIR;//文件夹根目录 /storage/emulated/0/WildmaIDCardCamera/
    }


    /**
     * 第一次检查权限，用在打开应用的时候请求应用需要的所有权限
     *
     * @param context
     * @param requestCode 请求码
     * @param permission  权限数组
     * @return
     */
    public static boolean checkPermissionFirst(Context context, int requestCode, String[] permission) {
        List<String> permissions = new ArrayList<String>();
        for (String per : permission) {
            int permissionCode = ActivityCompat.checkSelfPermission(context, per);
            if (permissionCode != PackageManager.PERMISSION_GRANTED) {
                permissions.add(per);
            }
        }
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions((Activity) context, permissions.toArray(new String[permissions.size()]), requestCode);
            return false;
        } else {
            return true;
        }
    }

    /**
     * 第二次检查权限，用在某个操作需要某个权限的时候调用
     *
     * @param context
     * @param requestCode 请求码
     * @param permission  权限数组
     * @return
     */
    public static boolean checkPermissionSecond(Context context, int requestCode, String[] permission) {

        List<String> permissions = new ArrayList<String>();
        for (String per : permission) {
            int permissionCode = ActivityCompat.checkSelfPermission(context, per);
            if (permissionCode != PackageManager.PERMISSION_GRANTED) {
                permissions.add(per);
            }
        }
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions((Activity) context, permissions.toArray(new String[permissions.size()]), requestCode);

            /*跳转到应用详情，让用户去打开权限*/
            Intent localIntent = new Intent();
            localIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT >= 9) {
                localIntent.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
                localIntent.setData(Uri.fromParts("package", context.getPackageName(), null));
            } else if (Build.VERSION.SDK_INT <= 8) {
                localIntent.setAction(Intent.ACTION_VIEW);
                localIntent.setClassName("com.android.settings", "com.android.settings.InstalledAppDetails");
                localIntent.putExtra("com.android.settings.ApplicationPkgName", context.getPackageName());
            }
            context.startActivity(localIntent);
            return false;
        } else {
            return true;
        }
    }

    public static class ImageUtils {

        /**
         * 保存图片
         *
         * @param src      源图片
         * @param filePath 要保存到的文件路径
         * @param format   格式
         * @return {@code true}: 成功<br>{@code false}: 失败
         */
        public static boolean save(Bitmap src, String filePath, Bitmap.CompressFormat format) {
            return save(src, FileUtils.getFileByPath(filePath), format, false);
        }

        /**
         * 保存图片
         *
         * @param src    源图片
         * @param file   要保存到的文件
         * @param format 格式
         * @return {@code true}: 成功<br>{@code false}: 失败
         */
        public static boolean save(Bitmap src, File file, Bitmap.CompressFormat format) {
            return save(src, file, format, false);
        }

        /**
         * 保存图片
         *
         * @param src      源图片
         * @param filePath 要保存到的文件路径
         * @param format   格式
         * @param recycle  是否回收
         * @return {@code true}: 成功<br>{@code false}: 失败
         */
        public static boolean save(Bitmap src, String filePath, Bitmap.CompressFormat format, boolean recycle) {
            return save(src, FileUtils.getFileByPath(filePath), format, recycle);
        }

        /**
         * 保存图片
         *
         * @param src     源图片
         * @param file    要保存到的文件
         * @param format  格式
         * @param recycle 是否回收
         * @return {@code true}: 成功<br>{@code false}: 失败
         */
        public static boolean save(Bitmap src, File file, Bitmap.CompressFormat format, boolean recycle) {
            if (isEmptyBitmap(src) || !FileUtils.createOrExistsFile(file)) {
                return false;
            }
            System.out.println(src.getWidth() + ", " + src.getHeight());
            OutputStream os = null;
            boolean ret = false;
            try {
                os = new BufferedOutputStream(new FileOutputStream(file));
                ret = src.compress(format, 100, os);
                if (recycle && !src.isRecycled()) {
                    src.recycle();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                FileUtils.closeIO(os);
            }
            return ret;
        }

        /**
         * 判断bitmap对象是否为空
         *
         * @param src 源图片
         * @return {@code true}: 是<br>{@code false}: 否
         */
        private static boolean isEmptyBitmap(Bitmap src) {
            return src == null || src.getWidth() == 0 || src.getHeight() == 0;
        }
    }

    /**
     * Author       wildma
     * Github       https://github.com/wildma
     * Date         2018/6/10
     * Desc	        ${文件相关工具类}
     */
    public static final class FileUtils {

        /**
         * 得到SD卡根目录，SD卡不可用则获取内部存储的根目录
         */
        public static File getRootPath() {
            File path = null;
            if (sdCardIsAvailable()) {
                path = Environment.getExternalStorageDirectory(); //SD卡根目录    /storage/emulated/0
            } else {
                path = Environment.getDataDirectory();//内部存储的根目录    /data
            }
            return path;
        }

        /**
         * SD卡是否可用
         */
        public static boolean sdCardIsAvailable() {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                File sd = new File(Environment.getExternalStorageDirectory().getPath());
                return sd.canWrite();
            } else
                return false;
        }

        /**
         * 判断目录是否存在，不存在则判断是否创建成功
         *
         * @param dirPath 文件路径
         * @return {@code true}: 存在或创建成功<br>{@code false}: 不存在或创建失败
         */
        public static boolean createOrExistsDir(String dirPath) {
            return createOrExistsDir(getFileByPath(dirPath));
        }

        /**
         * 判断目录是否存在，不存在则判断是否创建成功
         *
         * @param file 文件
         * @return {@code true}: 存在或创建成功<br>{@code false}: 不存在或创建失败
         */
        public static boolean createOrExistsDir(File file) {
            // 如果存在，是目录则返回true，是文件则返回false，不存在则返回是否创建成功
            return file != null && (file.exists() ? file.isDirectory() : file.mkdirs());
        }

        /**
         * 判断文件是否存在，不存在则判断是否创建成功
         *
         * @param filePath 文件路径
         * @return {@code true}: 存在或创建成功<br>{@code false}: 不存在或创建失败
         */
        public static boolean createOrExistsFile(String filePath) {
            return createOrExistsFile(getFileByPath(filePath));
        }

        /**
         * 判断文件是否存在，不存在则判断是否创建成功
         *
         * @param file 文件
         * @return {@code true}: 存在或创建成功<br>{@code false}: 不存在或创建失败
         */
        public static boolean createOrExistsFile(File file) {
            if (file == null)
                return false;
            // 如果存在，是文件则返回true，是目录则返回false
            if (file.exists())
                return file.isFile();
            if (!createOrExistsDir(file.getParentFile()))
                return false;
            try {
                return file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        /**
         * 根据文件路径获取文件
         *
         * @param filePath 文件路径
         * @return 文件
         */
        public static File getFileByPath(String filePath) {
            return isSpace(filePath) ? null : new File(filePath);
        }

        /**
         * 判断字符串是否为 null 或全为空白字符
         *
         * @param s
         * @return
         */
        private static boolean isSpace(final String s) {
            if (s == null)
                return true;
            for (int i = 0, len = s.length(); i < len; ++i) {
                if (!Character.isWhitespace(s.charAt(i))) {
                    return false;
                }
            }
            return true;
        }

        /**
         * 关闭IO
         *
         * @param closeables closeable
         */
        public static void closeIO(Closeable... closeables) {
            if (closeables == null)
                return;
            try {
                for (Closeable closeable : closeables) {
                    if (closeable != null) {
                        closeable.close();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

}
