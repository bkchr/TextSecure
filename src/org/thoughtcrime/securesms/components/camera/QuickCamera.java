package org.thoughtcrime.securesms.components.camera;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Build.VERSION_CODES;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Log;

import com.commonsware.cwac.camera.CameraHost.FailureReason;
import com.commonsware.cwac.camera.PictureTransaction;
import com.commonsware.cwac.camera.SimpleCameraHost;
import com.commonsware.cwac.camera.CameraView;

import java.util.List;

@SuppressWarnings("deprecation") public class QuickCamera extends CameraView {
  private static final String TAG = QuickCamera.class.getSimpleName();

  private QuickCameraListener quickCameraListener;
  private boolean             capturing;
  private boolean             started;
  private QuickCameraHost     cameraHost;
  private String              flashMode;

  public QuickCamera(Context context) {
    this(context, null);
  }

  public QuickCamera(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public QuickCamera(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
    cameraHost = new QuickCameraHost(context);
    setHost(cameraHost);
  }

  @Override
  public void onResume() {
    if (started) return;
    super.onResume();
    started = true;
  }

  @Override
  public void onPause() {
    if (!started) return;
    super.onPause();
    started = false;
  }

  public boolean isStarted() {
    return started;
  }

  public void takePicture(final Rect previewRect, final String flashMode) {
    if (capturing) {
      Log.w(TAG, "takePicture() called while previous capture pending.");
      return;
    }

    capturing = true;
    this.flashMode = flashMode;
    takePicture(false, true);
    /*
    setOneShotPreviewCallback(new Camera.PreviewCallback() {
      @Override
      public void onPreviewFrame(byte[] data, final Camera camera) {
        final int  rotation     = getCameraPictureOrientation();
        final Size previewSize  = cameraParameters.getPreviewSize();
        final Rect croppingRect = getCroppedRect(previewSize, previewRect, rotation);

        Log.w(TAG, "previewSize: " + previewSize.width + "x" + previewSize.height);
        Log.w(TAG, "previewFormat: " + cameraParameters.getPreviewFormat());
        Log.w(TAG, "croppingRect: " + croppingRect.toString());
        Log.w(TAG, "rotation: " + rotation);
        new AsyncTask<byte[], Void, byte[]>() {
          @Override
          protected byte[] doInBackground(byte[]... params) {
            byte[] data = params[0];
            try {

              return BitmapUtil.createFromNV21(data,
                                               previewSize.width,
                                               previewSize.height,
                                               rotation,
                                               croppingRect);
            } catch (IOException e) {
              return null;
            }
          }

          @Override
          protected void onPostExecute(byte[] imageBytes) {
            capturing = false;
            if (imageBytes != null && quickCameraListener != null) quickCameraListener.onImageCapture(imageBytes);
          }
        }.execute(data);
      }
    });*/
  }

  private Rect getCroppedRect(Size cameraPreviewSize, Rect visibleRect, int rotation) {
    final int previewWidth  = cameraPreviewSize.width;
    final int previewHeight = cameraPreviewSize.height;

    if (rotation % 180 > 0) rotateRect(visibleRect);

    float scale = (float) previewWidth / visibleRect.width();
    if (visibleRect.height() * scale > previewHeight) {
      scale = (float) previewHeight / visibleRect.height();
    }
    final float newWidth  = visibleRect.width()  * scale;
    final float newHeight = visibleRect.height() * scale;
    final float centerX   = previewWidth         / 2;
    final float centerY   = previewHeight        / 2;
    visibleRect.set((int) (centerX - newWidth  / 2),
                    (int) (centerY - newHeight / 2),
                    (int) (centerX + newWidth  / 2),
                    (int) (centerY + newHeight / 2));

    if (rotation % 180 > 0) rotateRect(visibleRect);
    return visibleRect;
  }

  @SuppressWarnings("SuspiciousNameCombination")
  private void rotateRect(Rect rect) {
    rect.set(rect.top, rect.left, rect.bottom, rect.right);
  }

  public boolean isMultipleCameras() {
    return Camera.getNumberOfCameras() > 1;
  }

  public boolean isRearCamera() {
    return cameraHost.getCameraId() == Camera.CameraInfo.CAMERA_FACING_BACK;
  }

  public void swapCamera() {
    cameraHost.swapCameraId();
    onPause();
    onResume();
  }

  public interface FlashInfoListener {
    void supportedModes(List<String> modes);
  }

  void setFlashInfoListener(FlashInfoListener listener) {
    cameraHost.setFlashInfoListener(listener);
  }

  public interface QuickCameraListener {
    void onImageCapture(@NonNull final byte[] imageBytes);
    void onCameraFail(FailureReason reason);
  }

  public void setQuickCameraListener(QuickCameraListener listener) {
    this.quickCameraListener = listener;
  }

  public class QuickCameraHost extends SimpleCameraHost {
    int cameraId = CameraInfo.CAMERA_FACING_BACK;
    private FlashInfoListener flashInfoListener = null;
    private QuickCameraListener quickCameraListener;

    public QuickCameraHost(Context context) {
      super(context);
    }

    @Override
    public void saveImage(PictureTransaction xact, byte[] image) {
      capturing = false;
      if (image != null && QuickCamera.this.quickCameraListener != null) QuickCamera.this.quickCameraListener.onImageCapture(image);
    }

    @Override
    public Parameters adjustPictureParameters(PictureTransaction xact, Parameters parameters) {
      parameters.setFlashMode(flashMode);
      return parameters;
    }

    @TargetApi(VERSION_CODES.ICE_CREAM_SANDWICH) @Override
    public Parameters adjustPreviewParameters(Parameters parameters) {
      List<String> focusModes = parameters.getSupportedFocusModes();
      if (focusModes.contains(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
        parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
      } else if (focusModes.contains(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
        parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
      }

      if (flashInfoListener != null) {
        flashInfoListener.supportedModes(parameters.getSupportedFlashModes());
      }

      return parameters;
    }

    @Override
    public int getCameraId() {
      return cameraId;
    }

    public void swapCameraId() {
      if (isMultipleCameras()) {
        if (cameraId == CameraInfo.CAMERA_FACING_BACK) cameraId = CameraInfo.CAMERA_FACING_FRONT;
        else                                           cameraId = CameraInfo.CAMERA_FACING_BACK;
      }
    }

    @Override
    public void onCameraFail(FailureReason reason) {
      super.onCameraFail(reason);
      if (QuickCamera.this.quickCameraListener != null) QuickCamera.this.quickCameraListener.onCameraFail(reason);
    }

    public void setFlashInfoListener(FlashInfoListener listener) {
      this.flashInfoListener = listener;
    }
  }
}