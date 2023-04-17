/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package com.serenegiant.usbcameratest7;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import com.serenegiant.encoder.IVideoEncoder;
import com.serenegiant.encoder.MediaEncoder;
import com.serenegiant.encoder.MediaVideoEncoder;
import com.serenegiant.glutils.RenderHandler;
import com.serenegiant.glutils.es1.GLHelper;
import com.serenegiant.media.manager.GlRenderManager;
import com.serenegiant.media.utils.GlUtil;
import com.serenegiant.utils.FpsCounter;
import com.serenegiant.widget.AspectRatioTextureView;
import com.serenegiant.widget.CameraViewInterface;
import com.serenegiant.widget.UVCCameraTextureView;

import java.nio.ByteBuffer;

/**
 * change the view size with keeping the specified aspect ratio.
 * if you set this view with in a FrameLayout and set property "android:layout_gravity="center",
 * you can show this view in the center of screen and keep the aspect ratio of content
 * XXX it is better that can set the aspect ratio as xml property
 */
public class UVCCameraSurfaceView extends SurfaceView    // API >= 14
        implements SurfaceHolder.Callback, CameraViewInterface {

    private static final boolean DEBUG = true;    // TODO set false on release
    private static final String TAG = "UVCCameraTextureView";

    private boolean mHasSurface;
    private RenderHandler mRenderHandler;
    private final Object mCaptureSync = new Object();
    private final Object mSurfaceSync = new Object();
    private Bitmap mTempBitmap;
    private boolean mReqesutCaptureStillImage;
    private Callback mCallback;
    private SurfaceTexture surfaceTexture;
    /**
     * for calculation of frame rate
     */
    private final FpsCounter mFpsCounter = new FpsCounter();

    public UVCCameraSurfaceView(final Context context) {
        this(context, null, 0);
    }

    public UVCCameraSurfaceView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public UVCCameraSurfaceView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        getHolder().addCallback(this);
    }

    @Override
    public void onResume() {
        if (DEBUG) Log.v(TAG, "onResume:" + mHasSurface);
        if (mHasSurface && mRenderHandler == null) {
            mRenderHandler = RenderHandler.createHandler(mFpsCounter, mPreviewSurface, surfaceTexture, cameraTexture, getWidth(), getHeight(), getContext());
        }
    }

    @Override
    public void onPause() {
        if (DEBUG) Log.v(TAG, "onPause:");
        if (mRenderHandler != null) {
            mRenderHandler.release();
            mRenderHandler = null;
        }
        if (mTempBitmap != null) {
            mTempBitmap.recycle();
            mTempBitmap = null;
        }
    }

    public void onSurfaceTextureAvailable(final SurfaceTexture surface, final int width, final int height) {
//
    }

    public void onSurfaceTextureSizeChanged(final SurfaceTexture surface, final int width, final int height) {
    }

    public boolean onSurfaceTextureDestroyed(final SurfaceTexture surface) {
//
        return true;
    }

    public void onSurfaceTextureUpdated(final SurfaceTexture surface) {

    }

    @Override
    public boolean hasSurface() {
        return mHasSurface;
    }

    /**
     * capture preview image as a bitmap
     * this method blocks current thread until bitmap is ready
     * if you call this method at almost same time from different thread,
     * the returned bitmap will be changed while you are processing the bitmap
     * (because we return same instance of bitmap on each call for memory saving)
     * if you need to call this method from multiple thread,
     * you should change this method(copy and return)
     */
    @Override
    public Bitmap captureStillImage() {
        synchronized (mCaptureSync) {
            mReqesutCaptureStillImage = true;
            try {
                mCaptureSync.wait();
            } catch (final InterruptedException e) {
            }
            return mTempBitmap;
        }
    }


    private Surface mPreviewSurface;
    private int cameraTexture;


    @Override
    public void setVideoEncoder(final IVideoEncoder encoder) {
        if (mRenderHandler != null)
            mRenderHandler.setVideoEncoder(encoder);
    }

    @Override
    public void setCallback(final Callback callback) {
        mCallback = callback;
    }

    @Override
    public SurfaceTexture getSurfaceTexture() {
        return surfaceTexture;
    }

    @Override
    public Surface getSurface() {
        synchronized (mSurfaceSync) {
            if (null == mPreviewSurface) {
                try {
                    mSurfaceSync.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        return mPreviewSurface;
    }

    public void resetFps() {
        mFpsCounter.reset();
    }

    /**
     * update frame rate of image processing
     */
    public void updateFps() {
        mFpsCounter.update();
    }

    /**
     * get current frame rate of image processing
     *
     * @return
     */
    public float getFps() {
        return mFpsCounter.getFps();
    }

    /**
     * get total frame rate from start
     *
     * @return
     */
    public float getTotalFps() {
        return mFpsCounter.getTotalFps();
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        synchronized (mSurfaceSync) {
            mPreviewSurface = surfaceHolder.getSurface();
            mSurfaceSync.notify();
        }
        cameraTexture = GlUtil.createRecordCameraTextureID();
        surfaceTexture = new SurfaceTexture(cameraTexture);
        mRenderHandler = RenderHandler.createHandler(mFpsCounter, mPreviewSurface, surfaceTexture, cameraTexture, getWidth(), getHeight(), getContext());
        if (mCallback != null) {
            mCallback.onSurfaceCreated(this, mPreviewSurface);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int i1, int i2) {
        if (DEBUG) Log.v(TAG, "onSurfaceTextureSizeChanged:");
        if (mRenderHandler != null) {
            mRenderHandler.resize(i1, i2);
        }
        if (mCallback != null) {
            mCallback.onSurfaceChanged(this, getSurface(), i1, i2);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        if (DEBUG) Log.v(TAG, "onSurfaceTextureDestroyed:");
        if (mRenderHandler != null) {
            mRenderHandler.release();
            mRenderHandler = null;
        }
        mHasSurface = false;
        if (mCallback != null) {
            mCallback.onSurfaceDestroy(this, getSurface());
        }
        if (mPreviewSurface != null) {
            mPreviewSurface.release();
            mPreviewSurface = null;
        }
    }

    @Override
    public void setAspectRatio(double v) {

    }

    @Override
    public void setAspectRatio(int i, int i1) {

    }

    @Override
    public double getAspectRatio() {
        return 0;
    }

    /**
     * render camera frames on this view on a private thread
     *
     * @author saki
     */
    private static final class RenderHandler extends Handler
            implements SurfaceTexture.OnFrameAvailableListener {

        private static final int MSG_REQUEST_RENDER = 1;
        private static final int MSG_SET_ENCODER = 2;
        private static final int MSG_CREATE_SURFACE = 3;
        private static final int MSG_RESIZE = 4;
        private static final int MSG_TERMINATE = 9;

        public RenderThread mThread;
        private boolean mIsActive = true;
        private final FpsCounter mFpsCounter;

        private RenderHandler(final FpsCounter counter, final RenderThread thread) {
            mThread = thread;
            mFpsCounter = counter;
        }

        public static final RenderHandler createHandler(final FpsCounter counter, final Surface dispSurface,
                                                        final SurfaceTexture surfaceTexture, final int textureId, final int width, final int height, Context context) {

            final RenderThread thread = new RenderThread(counter, dispSurface, surfaceTexture, textureId, width, height, context);
            thread.start();
            return thread.getHandler();
        }

        public final void setVideoEncoder(final IVideoEncoder encoder) {
            if (DEBUG) Log.v(TAG, "setVideoEncoder:");
            if (mIsActive)
                sendMessage(obtainMessage(MSG_SET_ENCODER, encoder));
        }


        public void resize(final int width, final int height) {
            if (DEBUG) Log.v(TAG, "resize:");
            if (mIsActive) {
                synchronized (mThread.mSync) {
                    sendMessage(obtainMessage(MSG_RESIZE, width, height));
                    try {
                        mThread.mSync.wait();
                    } catch (final InterruptedException e) {
                    }
                }
            }
        }

        public final void release() {
            if (DEBUG) Log.v(TAG, "release:");
            if (mIsActive) {
                mIsActive = false;
                removeMessages(MSG_REQUEST_RENDER);
                removeMessages(MSG_SET_ENCODER);
                sendEmptyMessage(MSG_TERMINATE);
            }
        }

        @Override
        public final void onFrameAvailable(final SurfaceTexture surfaceTexture) {
            if (mIsActive) {
                mFpsCounter.count();
                sendEmptyMessage(MSG_REQUEST_RENDER);
            }
        }

        @Override
        public final void handleMessage(final Message msg) {
            if (mThread == null) return;
            switch (msg.what) {
                case MSG_REQUEST_RENDER:
                    mThread.onDrawFrame();
                    break;
                case MSG_SET_ENCODER:
                    mThread.setEncoder((MediaEncoder) msg.obj);
                    break;
                case MSG_CREATE_SURFACE:
                    mThread.updatePreviewSurface();
                    break;
                case MSG_RESIZE:
                    mThread.resize(msg.arg1, msg.arg2);
                    break;
                case MSG_TERMINATE:
                    Looper.myLooper().quit();
                    mThread = null;
                    break;
                default:
                    super.handleMessage(msg);
            }
        }

        private static final class RenderThread extends Thread {
            private final Object mSync = new Object();
            private UVCCameraSurfaceView.RenderHandler mHandler;
            /**
             * IEglSurface instance related to this TextureView
             */
            private GlRenderManager glRenderManager;
            private int mTexId = -1;
            /**
             * SurfaceTexture instance to receive video images
             */
            private SurfaceTexture mPreviewSurface;
            public Surface mDispSurface;
            private final float[] mStMatrix = new float[16];
            private MediaEncoder mEncoder;
            private int mViewWidth, mViewHeight;
            private final FpsCounter mFpsCounter;
            private Context appContext;

            /**
             * constructor
             *
             * @param surface : drawing surface came from TexureView
             */
            public RenderThread(final FpsCounter fpsCounter, final Surface surface, final SurfaceTexture surfaceTexture, final int textureId, final int width, final int height, Context appContext) {
                mFpsCounter = fpsCounter;
                mPreviewSurface = surfaceTexture;
                mDispSurface = surface;
                mViewWidth = width;
                mViewHeight = height;
                mTexId = textureId;
                this.appContext = appContext;

                setName("RenderThread");
            }

            public final UVCCameraSurfaceView.RenderHandler getHandler() {
                if (DEBUG) Log.v(TAG, "RenderThread#getHandler:");
                synchronized (mSync) {
                    // create rendering thread
                    if (mHandler == null)
                        try {
                            mSync.wait();
                        } catch (final InterruptedException e) {
                        }
                }
                return mHandler;
            }

            public void resize(final int width, final int height) {
                if (((width > 0) && (width != mViewWidth)) || ((height > 0) && (height != mViewHeight))) {
                    mViewWidth = width;
                    mViewHeight = height;
                    updatePreviewSurface();
                } else {
                    synchronized (mSync) {
                        mSync.notifyAll();
                    }
                }
            }

            public final void updatePreviewSurface() {
                if (DEBUG) Log.i(TAG, "RenderThread#updatePreviewSurface:");


            }

            public final void setEncoder(final MediaEncoder encoder) {
//                if (DEBUG) Log.v(TAG, "RenderThread#setEncoder:encoder=" + encoder);
//                if (encoder != null && (encoder instanceof MediaVideoEncoder)) {
//                    ((MediaVideoEncoder) encoder).setEglContext(mEglSurface.getContext(), mTexId);
//                }
//                mEncoder = encoder;
            }

            /*
             * Now you can get frame data as ByteBuffer(as YUV/RGB565/RGBX/NV21 pixel format) using IFrameCallback interface
             * with UVCCamera#setFrameCallback instead of using following code samples.
             */
/*			// for part1
 			private static final int BUF_NUM = 1;
			private static final int BUF_STRIDE = 640 * 480;
			private static final int BUF_SIZE = BUF_STRIDE * BUF_NUM;
			int cnt = 0;
			int offset = 0;
			final int pixels[] = new int[BUF_SIZE];
			final IntBuffer buffer = IntBuffer.wrap(pixels); */
            // for part2
            int cnt = 0;
            private ByteBuffer buf = ByteBuffer.allocateDirect(640 * 480 * 4);

            /**
             * draw a frame (and request to draw for video capturing if it is necessary)
             */
            public final void onDrawFrame() {
                Log.d(TAG, "onDrawFrame");
//                mEglSurface.makeCurrent();
                // update texture(came from camera)
                // notify video encoder if it exist
                if (mEncoder != null) {
                    // notify to capturing thread that the camera frame is available.
                    if (mEncoder instanceof MediaVideoEncoder)
                        ((MediaVideoEncoder) mEncoder).frameAvailableSoon(mStMatrix);
                    else
                        mEncoder.frameAvailableSoon();
                }
                // draw to preview screen
                callFrameAvailable();
                //draw text
/*				// sample code to read pixels into Buffer and save as a Bitmap (part1)
				buffer.position(offset);
				GLES20.glReadPixels(0, 0, 640, 480, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
				if (++cnt == 100) { // save as a Bitmap, only once on this sample code
					// if you save every frame as a Bitmap, app will crash by Out of Memory exception...
					Log.i(TAG, "Capture image using glReadPixels:offset=" + offset);
					final Bitmap bitmap = createBitmap(pixels,offset,  640, 480);
					final File outputFile = MediaMuxerWrapper.getCaptureFile(Environment.DIRECTORY_DCIM, ".png");
					try {
						final BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile));
						try {
							try {
								bitmap.compress(CompressFormat.PNG, 100, os);
								os.flush();
								bitmap.recycle();
							} catch (IOException e) {
							}
						} finally {
							os.close();
						}
					} catch (FileNotFoundException e) {
					} catch (IOException e) {
					}
				}
				offset = (offset + BUF_STRIDE) % BUF_SIZE;
*/
                // sample code to read pixels into Buffer and save as a Bitmap (part2)
//		        buf.order(ByteOrder.LITTLE_ENDIAN);	// it is enough to call this only once.
//		        GLES20.glReadPixels(0, 0, 640, 480, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
//		        buf.rewind();
//				if (++cnt == 100) {	// save as a Bitmap, only once on this sample code
//					// if you save every frame as a Bitmap, app will crash by Out of Memory exception...
//					final File outputFile = MediaMuxerWrapper.getCaptureFile(Environment.DIRECTORY_DCIM, ".png");
//			        BufferedOutputStream os = null;
//					try {
//				        try {
//				            os = new BufferedOutputStream(new FileOutputStream(outputFile));
//				            Bitmap bmp = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
//				            bmp.copyPixelsFromBuffer(buf);
//				            bmp.compress(Bitmap.CompressFormat.PNG, 90, os);
//				            bmp.recycle();
//				        } finally {
//				            if (os != null) os.close();
//				        }
//					} catch (FileNotFoundException e) {
//					} catch (IOException e) {
//					}
//				}
            }

/*			// sample code to read pixels into IntBuffer and save as a Bitmap (part1)
			private static Bitmap createBitmap(final int[] pixels, final int offset, final int width, final int height) {
				final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
				paint.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[] {
						0, 0, 1, 0, 0,
						0, 1, 0, 0, 0,
						1, 0, 0, 0, 0,
						0, 0, 0, 1, 0
					})));

				final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
				final Canvas canvas = new Canvas(bitmap);

				final Matrix matrix = new Matrix();
				matrix.postScale(1.0f, -1.0f);
				matrix.postTranslate(0, height);
				canvas.concat(matrix);

				canvas.drawBitmap(pixels, offset, width, 0, 0, width, height, false, paint);

				return bitmap;
			} */

            @Override
            public final void run() {
                Log.d(TAG, getName() + " started");
                Looper.prepare();
                init();
                synchronized (mSync) {
                    mHandler = new UVCCameraSurfaceView.RenderHandler(mFpsCounter, this);
                    mSync.notify();
                }
                mPreviewSurface.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                    @Override
                    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callDrawFrame();
                            }
                        });
                    }
                });

                Looper.loop();

                Log.d(TAG, getName() + " finishing");
                release();
                synchronized (mSync) {
                    mHandler = null;
                    mSync.notify();
                }
            }

            protected void syncSize() {
//				if (glRenderManager != null && (glRenderManager.getmDisplayWidth() != renderSetting.getDisplayWidth()
//						|| glRenderManager.getmDisplayHeight() != renderSetting.getDisplayHeight()))
//					glRenderManager.onDisplaySizeChanged(renderSetting.getDisplayWidth(), renderSetting.getDisplayHeight());
//				if (glRenderManager != null && (glRenderManager.getmTextureWidth() != renderSetting.getRenderWidth()
//						|| glRenderManager.getmTextureHeight() != renderSetting.getRenderHeight()))
//					glRenderManager.onInputSizeChanged(renderSetting.getRenderWidth(), renderSetting.getRenderHeight());
            }

            //录制方向
            private int recordOrientation = 0;

            protected void callDrawFrame() {
                syncSize();
                //转换方向
                int recordRotate = recordOrientation;
                if (recordRotate == 90)
                    recordRotate = 270;
                else if (recordRotate == 270)
                    recordRotate = 90;
                try {
                    glRenderManager.drawFrame(false, recordRotate, false);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }


            //一帧准备完毕
            private void callFrameAvailable() {
                if (glRenderManager == null)
                    return;
                callDrawFrame();
            }

            private final void init() {
                if (DEBUG) Log.v(TAG, "RenderThread#init:");
                try {
                    glRenderManager.setCameraRotate(180);
                    glRenderManager = new GlRenderManager(appContext, mTexId, mDispSurface, mPreviewSurface);
                    glRenderManager.onInputSizeChanged(640, 480);
                    glRenderManager.onDisplaySizeChanged(mViewWidth, mViewHeight);


                    // notify to caller thread that previewSurface is ready
                } catch (GlUtil.OpenGlException e) {
                    e.printStackTrace();
                }
            }

            private final void release() {
                if (DEBUG) Log.v(TAG, "RenderThread#release:");
                if (glRenderManager != null) {
                    glRenderManager.release();
                    glRenderManager = null;
                    if (mPreviewSurface != null) {
                    }
                    mPreviewSurface.release();
                    mPreviewSurface = null;
                }
                if (mTexId >= 0) {
                    GLHelper.deleteTex(mTexId);
                    mTexId = -1;
                }
//                if (mEglSurface != null) {
//                    mEglSurface.release();
//                    mEglSurface = null;
//                }
//                if (mEgl != null) {
//                    mEgl.release();
//                    mEgl = null;
//                }
            }
        }
    }

}
