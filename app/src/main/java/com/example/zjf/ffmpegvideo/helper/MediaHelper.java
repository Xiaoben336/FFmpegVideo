package com.example.zjf.ffmpegvideo.helper;

import android.app.Activity;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.LinearLayout;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;


public class MediaHelper implements SurfaceHolder.Callback {
    private Activity activity;
    private MediaRecorder mMediaRecorder;
    private Camera mCamera;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private File targetDir;
    private String targetName;
    private File targetFile;
    private boolean isRecording;
    private GestureDetector mDetector;
    private boolean isZoomIn = false;
    private int or = 90;
    private int position = Camera.CameraInfo.CAMERA_FACING_BACK;//切换为后置摄像头

    public MediaHelper(Activity activity){
        this.activity = activity;
    }

    public void setTargetDir(File file){
        this.targetDir = file;
    }

    public void setTargetName(String name){
        this.targetName = name;
    }

    public String getTargetFilePath(){
        return targetFile.getPath();
    }

    public int getPosition(){
        return position;
    }

    public boolean deleteTargeFile(){
        if (targetFile.exists()){
            return targetFile.delete();
        } else {
            return false;
        }
    }

    /**
     * 初始化SurfaceView
     * @param view
     */
    public void setSurfaceView(SurfaceView view){
        this.mSurfaceView = view;
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);//设置SurfaceHolder类型
        mSurfaceHolder.addCallback(this);
        mDetector = new GestureDetector(activity,new ZoomGestureListener());
        mSurfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mDetector.onTouchEvent(event);
                return true;
            }
        });
    }

    /**
     * 双击SurfaceView时 Camera聚焦
     */
    private class ZoomGestureListener extends GestureDetector.SimpleOnGestureListener{
        //双击手势
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            super.onDoubleTap(e);
            if (!isZoomIn) {
                setZoom(20);
                isZoomIn = true;
            } else {
                setZoom(0);
                isZoomIn = false;
            }
            return true;
        }
    }

    /**
     * 用于设置相机焦距，其参数是一个整型的参数，该参数的范围是0到Camera.getParameters().getMaxZoom()。
     * @param zoomValue
     */
    private void setZoom(int zoomValue){
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            if (parameters.isZoomSupported()){
                int maxZoom = parameters.getMaxZoom();
                if (maxZoom == 0) {
                    return;
                }

                if (zoomValue > maxZoom) {
                    zoomValue = maxZoom;
                }

                parameters.setZoom(zoomValue);
                mCamera.setParameters(parameters);
            }
        }
    }

    public boolean isRecording(){
        return isRecording;
    }

    /**
     * 视频录制方法
     */
    public void record(){
        if (isRecording) {//正在录制。。。
            mMediaRecorder.stop();
            targetFile.delete();
            releaseMediaRecorder();
            mCamera.lock();
            isRecording = false;
        } else {
            startRecordThread();
        }
    }

    /**
     * 开启视频录制线程
     */
    private void startRecordThread() {
        if (prepareRecord()) {
            try {
                mMediaRecorder.start();
                isRecording =  true;
            } catch (RuntimeException e) {
                e.printStackTrace();
                releaseMediaRecorder();
            }
        }
    }

    /**
     * MediaRecorder初始化
     * @return MediaRecorder是否成功初始化
     */
    private boolean prepareRecord() {
        try {
            mMediaRecorder = new MediaRecorder();
            mCamera.unlock();
            mMediaRecorder.setCamera(mCamera);
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mMediaRecorder.setVideoSize(1280,720);
            mMediaRecorder.setVideoEncodingBitRate(2 * 1024 * 1024);//2MBPS
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);//视频编码格式
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);//音频编码格式
            if (position == Camera.CameraInfo.CAMERA_FACING_BACK) {
                mMediaRecorder.setOrientationHint(or);
            } else {
                mMediaRecorder.setOrientationHint(270);
            }

            targetFile = new File(targetDir,targetName);
            mMediaRecorder.setOutputFile(targetFile.getPath());
        } catch (Exception e) {
            e.printStackTrace();
            releaseMediaRecorder();
            return false;
        }

        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
            releaseMediaRecorder();
            return false;
        }
        return true;
    }


    /**
     * 停止录制视频
     */
    public void stopRecordSave(){
        if (isRecording) {
            isRecording = false;
            try {
                mMediaRecorder.stop();
            } catch (RuntimeException r) {
                r.printStackTrace();
            } finally {
                releaseMediaRecorder();
            }


        }
    }

    /**
     * 停止录制视频，但不保存录制文件
     */
    public void stopRecordUnSave(){
        if (isRecording) {
            isRecording = false;
            try {
                mMediaRecorder.stop();
            } catch (RuntimeException e) {
                if (targetFile.exists()) {
                    targetFile.delete();
                }
            } finally {
                releaseMediaRecorder();
            }
            if (targetFile.exists()) {
                targetFile.delete();
            }
        }
    }

    /**
     * 切换摄像头
     */
    public void autoChangeCamera(){
        if (position == Camera.CameraInfo.CAMERA_FACING_BACK) {
            position = Camera.CameraInfo.CAMERA_FACING_FRONT;
        } else {
            position = Camera.CameraInfo.CAMERA_FACING_BACK;
        }

        releaseCamera();
        stopRecordUnSave();
        startPreView(mSurfaceHolder);
    }


    /**
     * 处理变形问题,这里是预览图尺寸处理的方法，就是在这把宽高调换，
     * @param sizes
     * @param w
     * @param h
     * @return
     */
    public Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (sizes == null) {
            return null;
        }
        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        int targetHeight = h;
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
        return optimalSize;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSurfaceHolder = holder;
        startPreView(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mCamera != null) {
            releaseCamera();
        }
        if (mMediaRecorder != null) {
            releaseMediaRecorder();
        }
    }

    /**
     *
     * @param holder
     */
    private void startPreView(SurfaceHolder holder) {
        if (mCamera == null) {
            mCamera = Camera.open(position);
        }

        if (mCamera != null) {
            mCamera.setDisplayOrientation(or);
            try {
                // //surface创建，设置预览SurfaceHolder
                mCamera.setPreviewDisplay(holder);
                Camera.Parameters parameters = mCamera.getParameters();
                //获取支持的预览尺寸列表
                List<Camera.Size> mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();

                if (mSupportedPreviewSizes != null) {
                    int width = mSurfaceView.getWidth();
                    int height = mSurfaceView.getHeight();
                    //预览图尺寸
                    Camera.Size mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes,Math.max(width,height),Math.min(width,height));
                    parameters.setPictureSize(mPreviewSize.width,mPreviewSize.height);
                }
                List<String> focusModes = parameters.getSupportedFocusModes();
                if (focusModes != null) {
                    for (String mode : focusModes) {
                        if(mode.contains(Camera.Parameters.FOCUS_MODE_AUTO)){
                            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                        }
                    }
                }
                mCamera.setParameters(parameters);
                mCamera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *释放Camera对象
     */
    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
    }

    /**
     * 释放MediaRecorder对象
     */
    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }
}
