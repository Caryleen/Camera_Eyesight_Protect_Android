package com.example.acer.camerafacecsdn;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity {
    private static void Log(String message){
        Log.i(MainActivity.class.getName(), message);
    }

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray(); //为了使照片竖直显示
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private TextureView cView;//用于相机预览
    private TextureView rView;//用于标注人脸
    private ImageView imageView;//拍照照片显示
    private TextView textV;
    private TextView textView3;
    private TextView textView;
    private Button btnFront;
    private Button btnBack;
    private Button btnClose;
    private Button btnSet;
    private SeekBar seekBar;
    private Vibrator mVibrator;
    private int s;
    private int ad = 30000;
    private int stad = 400000;
    private int count = 60;

    private Surface previewSurface;//预览Surface
    private ImageReader cImageReader;
    private Surface captureSurface;//拍照Surface

    HandlerThread cHandlerThread;//相机处理线程
    Handler cHandler;//相机处理
    CameraDevice cDevice;
    CameraCaptureSession cSession;

    CameraDevice.StateCallback cDeviceOpenCallback = null;//相机开启回调

    CaptureRequest.Builder previewRequestBuilder;//预览请求构建
    CaptureRequest previewRequest;//预览请求
    CameraCaptureSession.CaptureCallback previewCallback;//预览回调

    CaptureRequest.Builder captureRequestBuilder;

    CameraCaptureSession.CaptureCallback captureCallback;
    int[] faceDetectModes;
    // Rect rRect;//相机成像矩形
    Size cPixelSize;//相机成像尺寸
    int cOrientation;
    Size captureSize;
    boolean isFront;
    Paint pb;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );
        //GlobalExceptionHandler catchHandler = GlobalExceptionHandler.getInstance();
        //catchHandler.init(this.getApplication());
        initVIew();
        mVibrator=(Vibrator)getApplication().getSystemService(Service.VIBRATOR_SERVICE);
    }
    /**
     * 初始化界面
     */
    private void initVIew() {
        cView = (TextureView) findViewById(R.id.cView);
        rView = (TextureView) findViewById(R.id.rView);
        imageView = (ImageView) findViewById(R.id.imageView);
        textView = (TextView) findViewById(R.id.textView);

        textV = (TextView)findViewById(R.id.textView2);
        textV.setMovementMethod(ScrollingMovementMethod.getInstance());

        textView3 = (TextView) findViewById(R.id.textView3);
        textView3.setGravity(Gravity.CENTER);

        btnFront = (Button) findViewById(R.id.btnFront);
        btnBack = (Button) findViewById(R.id.btnBack);
        btnClose = (Button) findViewById(R.id.btnClose);
        btnSet = (Button)findViewById(R.id.btnSet );
        seekBar = (SeekBar)findViewById(R.id.addNum);

        rView.setAlpha(0.9f); //隐藏背景色，以免标注人脸时挡住预览画面

        btnFront.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera(true);
            }
        });
/*
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera(false);
            }
        });*/

        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeCamera();
                mVibrator.cancel();
            }
        });

        btnSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stad = s ;
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    ad = progress ;
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                  ad = seekBar.getProgress();
            }
        }); /** 滑动条 */

    }  //btn 监听，对应各方法

    @SuppressLint("NewApi")
    private void openCamera(boolean isFront) {
        closeCamera();
        this.isFront = isFront;
        String cId = null;
        if (isFront) {
            cId = CameraCharacteristics.LENS_FACING_BACK + "";
        } else {
            cId = CameraCharacteristics.LENS_FACING_FRONT + "";
        }
        CameraManager cManager = (CameraManager) getSystemService( Context.CAMERA_SERVICE);
        try {
            //获取开启相机的相关参数
            CameraCharacteristics characteristics = cManager.getCameraCharacteristics(cId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            cOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);//获取相机角度
            cPixelSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);//获取成像尺寸，同上
            //可用于判断是否支持人脸检测，以及支持到哪种程度
            faceDetectModes = characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);//支持的人脸检测模式

            /**--此处写死  560*420     实际从预览尺寸列表选择---*/
            Size sSize = new Size(560,420);//previewSizes[0];

            //设置预览尺寸（避免控件尺寸与预览画面尺寸不一致时画面变形）
            cView.getSurfaceTexture().setDefaultBufferSize(sSize.getWidth(),sSize.getHeight());

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                Toast.makeText(this,"请授予摄像头权限",Toast.LENGTH_LONG).show();
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA}, 0);
                return;
            }
            //根据摄像头ID，开启摄像头
            try {
                cManager.openCamera(cId, getCDeviceOpenCallback(), getCHandler());
            } catch (CameraAccessException e) {
                Log(Log.getStackTraceString(e));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } catch (CameraAccessException e) {
            Log(Log.getStackTraceString(e));
        }
    }    //打开摄像头，参数配置

    @SuppressLint("NewApi")
    private void closeCamera(){

        if (cSession != null){
            cSession.close();
            cSession = null;
        }
        if (cDevice!=null){
            cDevice.close();
            cDevice = null;
        }
        if (cImageReader != null) {
            cImageReader.close();
            cImageReader = null;
            captureRequestBuilder = null;
        }
        if(cHandlerThread!=null){
            cHandlerThread.quitSafely();
            try {
                cHandlerThread.join();
                cHandlerThread = null;
                cHandler = null;
            } catch (InterruptedException e) {
                Log(Log.getStackTraceString(e));
            }
        }

    }        //关闭摄像头


    /**=====初始化并获取相机开启回调对象。当准备就绪后，发起预览请求=====*/
    @SuppressLint("NewApi")
    private CameraDevice.StateCallback getCDeviceOpenCallback(){
        if(cDeviceOpenCallback == null){
            cDeviceOpenCallback = new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cDevice = camera;
                    try {
                        //创建Session，需先完成画面呈现目标（此处为预览和拍照Surface）的初始化
                        camera.createCaptureSession( Arrays.asList(getPreviewSurface(), getCaptureSurface()), new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession session) {
                                cSession = session;
                                //构建预览请求，并发起请求
                                Log("[发出预览请求]");
                                try {
                                    session.setRepeatingRequest(getPreviewRequest(), getPreviewCallback(), getCHandler());
                                } catch (CameraAccessException e) {
                                    Log(Log.getStackTraceString(e));
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                session.close();
                            }
                        }, getCHandler());
                    } catch (CameraAccessException e) {
                        Log(Log.getStackTraceString(e));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                }
            };
        }
        return cDeviceOpenCallback;
    }

    /**
     * 初始化并获取相机线程处理
     * @return cHandler
     */
    private Handler getCHandler() throws InterruptedException {
        if(cHandler==null){
            //单独开一个线程给相机使用
            cHandlerThread = new HandlerThread("cHandlerThread");
            cHandlerThread.start();
            cHandler = new Handler(cHandlerThread.getLooper());
            Thread.sleep(500);
        }
        return cHandler;
    }


    @Override    /**---按返回键不会停止Activity */
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    /**获取支持的最高人脸检测级别 @return faceDetectModes[faceDetectModes.length-1]  */
    private int getFaceDetectMode(){
        if(faceDetectModes == null){
            return CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL;
        }else{
            return faceDetectModes[faceDetectModes.length-1];
        }
    }

    /**---------------------------------预览相关---------------------------------
     *
     * 初始化并获取预览回调对象
     * @return previewCallback
     */
    @SuppressLint("NewApi")
    private CameraCaptureSession.CaptureCallback getPreviewCallback (){
        if(previewCallback == null){
            previewCallback = new CameraCaptureSession.CaptureCallback(){
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    try {
                        MainActivity.this.onCameraImagePreviewed(result);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
            };
        }
        return previewCallback;
    }

    /**
     * 生成并获取预览请求
     * @return previewRequest
     */
    @SuppressLint("NewApi")
    private CaptureRequest getPreviewRequest(){
        previewRequest = getPreviewRequestBuilder().build();
        return previewRequest;
    }

    /**
     * 初始化并获取预览请求构建对象，进行通用配置，并每次获取时进行人脸检测级别配置
     * @return previewRequestBuilder
     */
    @SuppressLint("NewApi")
    private CaptureRequest.Builder getPreviewRequestBuilder(){
        if(previewRequestBuilder == null){
            try {
                previewRequestBuilder = cSession.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                previewRequestBuilder.addTarget(getPreviewSurface());
                previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);//自动曝光、白平衡、对焦
            } catch (CameraAccessException e) {
                Log(Log.getStackTraceString(e));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        previewRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,getFaceDetectMode());//设置人脸检测级别
        return previewRequestBuilder;
    }

    /**
     * 获取预览Surface
     * @return
     */
    private Surface getPreviewSurface() throws InterruptedException {
        if(previewSurface == null){
            previewSurface = new Surface(cView.getSurfaceTexture());

        }
        return previewSurface;
    }

    /**toast自定义时长***/
    public void showMyToast(final Toast toast, final int cnt) {

        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {

                    toast.show();

            }
        }, 0, 3000);
     new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                toast.cancel();
                timer.cancel();
            }
        }, cnt );//delay为空，执行一次
    }


    /**
     * 处理相机画面处理完成事件，获取检测到的人脸坐标，换算并绘制方框
     */
    @SuppressLint({"NewApi", "LocalSuppress"})
    private void onCameraImagePreviewed(CaptureResult result) throws InterruptedException {

           Face faces[] = result.get(CaptureResult.STATISTICS_FACES);
            showMessage(false,"人脸个数:["+faces.length+"]");
        Canvas canvas = rView.lockCanvas();
        canvas.drawColor( Color.TRANSPARENT, PorterDuff.Mode.CLEAR);//旧画面清理覆盖

        if(faces.length>0 ){
            for(int i=0;i<faces.length;i++){
                @SuppressLint("NewApi") Rect fRect = faces[i].getBounds();
              //  Log("[R"+i+"]:[left:"+fRect.left+",top:"+fRect.top+",right:"+fRect.right+",bottom:"+fRect.bottom+"]");
              //  showMessage(true,"[R"+i+"]:[left:"+fRect.left+",top:"+fRect.top+",right:"+fRect.right+",bottom:"+fRect.bottom+"]");
                //人脸检测坐标基于相机成像画面尺寸以及坐标原点。此处进行比例换算
                //成像画面与方框绘制画布长宽比比例（同画面角度情况下的长宽比例（此处前后摄像头成像画面相对预览画面倒置（±90°），计算比例时长宽互换））
                float scaleWidth = canvas.getHeight()*1.0f/cPixelSize.getWidth();
                float scaleHeight = canvas.getWidth()*1.0f/cPixelSize.getHeight();

                //坐标缩放
                int l = (int) (fRect.left*scaleWidth);
                int t = (int) (fRect.top*scaleHeight);
                int r = (int) (fRect.right*scaleWidth);
                int b = (int) (fRect.bottom*scaleHeight);
                s = (int) (t-b)*(l-r) ;
              //  Log("[T"+i+"]:[left:"+l+",top:"+t+",right:"+r+",bottom:"+b+"]");
               // showMessage(true,"[T"+i+"]:[left:"+l+",top:"+t+",right:"+r+",bottom:"+b+"]");
                showMessage(true,"[Face"+i+"]:[标准:"+(stad+ad)+",滑条:"+ad+",当前:"+s+"]");

            //人脸检测坐标基于相机成像画面尺寸以及坐标原点。此处进行坐标转换以及原点(0,0)换算
            //人脸检测：坐标原点为相机成像画面的左上角，left、top、bottom、right以成像画面左上下右为基准
            //画面旋转后：原点位置不一样，根据相机成像画面的旋转角度需要换算到画布的左上角，left、top、bottom、right基准也与原先不一样，
            //如相对预览画面相机成像画面角度为90°那么成像画面坐标的top，在预览画面就为left。如果再翻转，那成像画面的top就为预览画面的right，且坐标起点为右，需要换算到左边


                ImageView imageView = new ImageView(this);
                imageView.setImageResource(R.drawable.eyet); /**==提示图片文件名===*/

                LinearLayout layout = new LinearLayout(this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.addView(imageView);
                Toast toast = new Toast(this);      /**=====提示设置======*/
                toast.setView(layout);
                toast.setGravity(Gravity.CENTER,0,0);
                toast.setDuration(Toast.LENGTH_LONG);


                if  ( s > stad + ad  && count <= 10 ){
                   showMyToast(toast,800);             /**=====距离提示======*/
                    mVibrator.vibrate(new long[]{100,40,300,40,300,40},-1);
                   count = 50;

                }
                count--;


                if(isFront){
                    //此处前置摄像头成像画面相对于预览画面顺时针90°+翻转。left、top、bottom、right变为bottom、right、top、left，并且由于坐标原点由左上角变为右下角，X,Y方向都要进行坐标换算
                    canvas.drawRect(canvas.getWidth()-b,canvas.getHeight()-r,canvas.getWidth()-t,canvas.getHeight()-l,getPaint());
                }else{
                    //此处后置摄像头成像画面相对于预览画面顺时针270°，left、top、bottom、right变为bottom、left、top、right，并且由于坐标原点由左上角变为左下角，Y方向需要进行坐标换算
                    canvas.drawRect(canvas.getWidth()-b,l,canvas.getWidth()-t,r,getPaint());
                }
            }
        }
        rView.unlockCanvasAndPost(canvas);
    }

    /**-----初始化画笔----*/
    private Paint getPaint(){
        if(pb == null){
            pb =new Paint();
            pb.setColor(Color.GREEN);
            pb.setStrokeWidth(10);
            pb.setStyle(Paint.Style.STROKE);//使绘制的矩形中空
        }
        return pb;
    }

    /**-----初始化拍照相关---*/
    @SuppressLint("NewApi")
    private Surface getCaptureSurface() throws InterruptedException {
        if(cImageReader == null){
            cImageReader = ImageReader.newInstance(getCaptureSize().getWidth(), getCaptureSize().getHeight(), ImageFormat.JPEG, 2);
            cImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener(){
                @Override
                public void onImageAvailable(ImageReader reader) {

                }}, getCHandler());
            captureSurface = cImageReader.getSurface();
        }
        return captureSurface;
    }


    private Size getCaptureSize(){
        if(captureSize!=null){
            return captureSize;
        }else{
            return cPixelSize;
        }
    }   //获取拍照尺寸

    /**-----显示信息---*/
    private void showMessage(final boolean add, final String message){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(add){
                    textView.setText(textView.getText()+"\n"+message);
                }else{
                    textView.setText(message);
                }
            }
        });
    }
}
