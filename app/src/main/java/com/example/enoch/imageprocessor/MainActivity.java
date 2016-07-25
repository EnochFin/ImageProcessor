package com.example.enoch.imageprocessor;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private final int REQUEST_CAMERA = 10;

    private CameraDevice cameraDevice;
    private String camId;
    private CameraDevice.StateCallback stateCallback;

    private CaptureRequest previewCaptureRequest;
    private CaptureRequest.Builder previewCaptureRequestBuilder;

    private CameraCaptureSession cameraCaptureSession;
    private CameraCaptureSession.CaptureCallback sessionCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);
                }
            };

    private Size imageSize;

    private TextView directionDesc;
    private TextView averageXText;
    private TextView blackCounter;
    private ImageView processedImage;
    private TextureView textureView;
    private TextureView.SurfaceTextureListener surfaceTextureListener;

    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";
    private UsbDevice device;
    private UsbManager usbManager;
    private UsbDeviceConnection connection;
    private UsbSerialDevice serialPort;
    private final int MAX_DENSITY_GAP = 3;

    private String currDir = " ";
    private final String RIGHT_CODE = "r";


    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { //Broadcast Receiver to automatically start and stop the Serial connection.
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                    boolean granted =
                            intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                    if (granted) {
                        connection = usbManager.openDevice(device);
                        serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
                        if (serialPort != null) {
                            if (serialPort.open()) { //Set Serial Connection Parameters.
                                serialPort.setBaudRate(9600);
                                serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                                serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                                serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                                serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                            } else {
                                Toast.makeText(getApplicationContext(), "Port not open", Toast.LENGTH_SHORT).show();
                                Log.d("SERIAL", "PORT NOT OPEN");
                            }
                        } else {
                            Toast.makeText(getApplicationContext(), "Port is null", Toast.LENGTH_SHORT).show();
                            Log.d("SERIAL", "PORT IS NULL");
                        }
                    } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                        Toast.makeText(getApplicationContext(), "ACTION USB DEVICE ATTACHED", Toast.LENGTH_SHORT).show();
                        onClickStart(null);
                    } else {
                        Toast.makeText(getApplicationContext(), "Perm not granted", Toast.LENGTH_SHORT).show();
                        Log.d("SERIAL", "PERM NOT GRANTED");
                    }
                }
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), "ERROR in broadcast reciever", Toast.LENGTH_SHORT).show();
            }
        }

        ;
    };

    @Override
    public void onResume() {
        super.onResume();
        System.out.println("onResume");
        if (textureView.isAvailable()) {

        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    public void setupCamera(int width, int height) {

        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics camCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (camCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_BACK) {
                    continue;
                }
                StreamConfigurationMap map =
                        camCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                imageSize = getPreferredImageSize(map.getOutputSizes(SurfaceTexture.class), width, height);
                camId = cameraId;
                return;
            }

        } catch (CameraAccessException ex) {
            ex.printStackTrace();
        }
    }

    private Size getPreferredImageSize(Size[] mapSizes, int width, int height) {
        List<Size> collectorSizes = new ArrayList<>();
        for (Size option : mapSizes) {
            if (width > height) {
                if (option.getWidth() > width &&
                        option.getHeight() > height) {
                    collectorSizes.add(option);
                }
            } else {
                if (option.getWidth() > height &&
                        option.getHeight() > width) {
                    collectorSizes.add(option);
                }
            }
        }
        if (collectorSizes.size() > 0) {
            return Collections.min(collectorSizes, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                }
            });
        }
        return mapSizes[0];
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setTitle("Image Processor");

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        setContentView(com.example.enoch.imageprocessor.R.layout.activity_main);
        surfaceTextureListener =
                new TextureView.SurfaceTextureListener() {
                    @Override
                    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                        setupCamera(width, height);
                        openCamera();
                        Toast.makeText(getApplicationContext(), "opened camera", Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

                    }

                    @Override
                    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                        return false;
                    }

                    @Override
                    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                        processImage(textureView.getBitmap());
                    }
                };

        stateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                Toast.makeText(getApplicationContext(),
                        "onOpened state callback",
                        Toast.LENGTH_LONG).show();
                setCameraDevice(camera);
                createCameraPreviewSession();
            }

            @Override
            public void onDisconnected(CameraDevice camera) {
                camera.close();
                setCameraDevice(null);
            }

            @Override
            public void onError(CameraDevice camera, int error) {
                onDisconnected(camera);
            }
        };
        textureView = (TextureView) findViewById(R.id.textureView);
        processedImage = (ImageView) findViewById(com.example.enoch.imageprocessor.R.id.processedImage);
        directionDesc = (TextView) findViewById(R.id.direction);
        blackCounter = (TextView) findViewById(R.id.blackCounter);
        averageXText = (TextView) findViewById(R.id.avgXView);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);
    }

    public void onClickStart(View view) {
        try {
            HashMap usbDevices = usbManager.getDeviceList();
            int id = 0;
            boolean keeper = false;
            if (!usbDevices.isEmpty()) {
                boolean keep = false;
                keeper = keep;
                for (Object entry : usbDevices.entrySet()) {
                    Map.Entry mEntry = (Map.Entry) entry;
                    device = (UsbDevice) mEntry.getValue();
                    int deviceVID = device.getVendorId();
                    id = deviceVID;
                    if (deviceVID == 0x2341)//Arduino Vendor ID
                    {
                        PendingIntent pi = PendingIntent.getBroadcast(this, 0,
                                new Intent(ACTION_USB_PERMISSION), 0);
                        usbManager.requestPermission(device, pi);
                        keep = false;
                    } else {
                        connection = null;
                        device = null;
                    }

                    if (!keep)
                        break;
                }
            }
            String str = "onclick enasdasdd" + keeper + id;
            Toast.makeText(getApplicationContext(), str, Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            Toast.makeText(getApplicationContext(), ex.toString(), Toast.LENGTH_LONG).show();
        }
    }

    public void setCameraDevice(CameraDevice cameraDevice) {
        this.cameraDevice = cameraDevice;
    }

    public void openCamera() {
        try {
            int permissionCheck = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.CAMERA);
            if (permissionCheck == PackageManager.PERMISSION_DENIED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        REQUEST_CAMERA);
            } else {
                try {
                    CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                    cameraManager.openCamera(camId, stateCallback, null);
                } catch (CameraAccessException ex) {
                    ex.printStackTrace();
                } catch (SecurityException ex) {
                    ex.printStackTrace();
                }
            }
        } catch (SecurityException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

        switch (requestCode) {
            case REQUEST_CAMERA: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    try {
                        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                        cameraManager.openCamera(camId, stateCallback, null);
                    } catch (CameraAccessException ex) {
                        ex.printStackTrace();
                    } catch (SecurityException ex) {
                        ex.printStackTrace();
                    }
                }
                return;
            }
        }
    }

    public void createCameraPreviewSession() {
        try {

            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(imageSize.getWidth(), imageSize.getHeight());
            Surface surface = new Surface(surfaceTexture);
            previewCaptureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewCaptureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if (cameraDevice == null)
                        return;
                    try {
                        previewCaptureRequest = previewCaptureRequestBuilder.build();
                        cameraCaptureSession = session;
                        cameraCaptureSession.setRepeatingRequest(
                                previewCaptureRequest,
                                sessionCaptureCallback,
                                null);
                    } catch (CameraAccessException ex) {
                        ex.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, null);

        } catch (android.hardware.camera2.CameraAccessException ex) {
            ex.printStackTrace();
        } catch (SecurityException ex) {
            ex.printStackTrace();
        }
    }

    private void processImage(Bitmap bitmap) {
        Bitmap img = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() / 10, bitmap.getHeight() / 10, false);
        int height = img.getHeight();
        int width = img.getWidth();

        Float ballYLocation = 0.0f;
        int rgb, red = 0, green = 0, blue = 0, blackCount = 0;
        boolean firstPass = true;

        //for all rows
        for (int x = 0; x < width; x++) {
            ArrayList<Integer> yBlackPositions = new ArrayList();
            //find orange dots
            //for all pixels in row
            for (int y = 0; y < height; y++) {
                rgb = img.getPixel(x, y);
                red = (rgb >> 16) & 0x000000FF;
                green = (rgb >> 8) & 0x000000FF;
                blue = (rgb) & 0x000000FF;


                //if pixel is close to orange
                if ((red >= 200) && (green <= red / 2) && (blue <= red / 2)) {
                    //mark the xPos in list
                    //the pixel is "orange"
                    yBlackPositions.add(y);
                    blackCount++;
                    img.setPixel(x, y, Color.rgb(0, 0, 0));
                }
            }

            //if there are orange dots
            if (yBlackPositions.size() > 0) {
                if(firstPass) {
                    ballYLocation = new Float(yBlackPositions.get(yBlackPositions.size() / 2));
                } else {
                    ballYLocation = ballYLocation + new Float(yBlackPositions.get(yBlackPositions.size() / 2)) / 2;
                }
//                averageXText.setText("no more avg");
                averageXText.setText(String.valueOf(yBlackPositions.get(0)) + ":" + String.valueOf(yBlackPositions.size()) + ":" + String.valueOf(yBlackPositions.get(yBlackPositions.size() / 2)) + ":" + String.valueOf(yBlackPositions.get(yBlackPositions.size() - 1)));
            }

        }



        rgb = img.getPixel(width / 2, height / 2);
        red = (rgb >> 16) & 0x000000FF;
        green = (rgb >> 8) & 0x000000FF;
        blue = (rgb) & 0x000000FF;

        directionDesc.setText(red + ":" + green + ":" + blue);
        img.setPixel(width /2, height /2, Color.rgb(0, 0, 255));

        for (int y = 0; y < width; y++)
            img.setPixel(y, Math.round(ballYLocation), Color.rgb(255, 255, 255));
        blackCounter.setText(String.valueOf(blackCount));
        if (blackCount < 650 && blackCount > 0) {
            if (serialPort != null)
                try {
                    if (ballYLocation > width - 30) {
                        if (currDir != "r") {
                            serialPort.write(new byte[]{'r'});
                            currDir = "r";
                        }
                        directionDesc.setText(String.valueOf('r' + " :" + ballYLocation));
                    } else if (ballYLocation < 30) {
                        if (currDir != "l") {
                            serialPort.write(new byte[]{'r'});
                            currDir = "r";
                        }
                        directionDesc.setText(String.valueOf('l' + " :" + ballYLocation));
                    } else {
                        if (currDir != "f") {
                            serialPort.write(new byte[]{'f'});
                            currDir = "f";
                        }
                        directionDesc.setText(String.valueOf('f' + ":" + ballYLocation));
                    }
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), "ERROR in onclick", Toast.LENGTH_SHORT);
                }
        }
        processedImage.setImageBitmap(Bitmap.createScaledBitmap(img, 400, 900, false));
    }

    private int findDenseLoc(Integer[] orangeLocations) {
        int denseLoc = 0;
        Integer[] largestSegment = {};
        int start = orangeLocations[0];
        ArrayList<Integer> currentSegment = new ArrayList();

        for (int i = 0; i < orangeLocations.length - 1; i++) {

            if (orangeLocations[i + 1] - orangeLocations[i] <= MAX_DENSITY_GAP) {
                //gap is small enough
                currentSegment.add(orangeLocations[i]);
            } else {
                //gap is too big
                start = orangeLocations[i + 1];
                if (currentSegment.size() > largestSegment.length) {
                    largestSegment = currentSegment.toArray(new Integer[currentSegment.size()]);
                }
            }
        }

        //has 2 elements
        if (largestSegment.length > 1) {
            denseLoc = largestSegment[0] + largestSegment[largestSegment.length - 1] / 2;
        }

        return denseLoc;
    }
}
