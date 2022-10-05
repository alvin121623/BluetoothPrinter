package application.aku.bluetoothprinter;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import application.aku.bluetoothprinter.METHOD.Custom_bluetooth;
import application.aku.bluetoothprinter.METHOD.Object_bluetooth;
import application.aku.bluetoothprinter.METHOD.PrinterCommands;
import application.aku.bluetoothprinter.METHOD.Utils;
import butterknife.BindView;
import butterknife.ButterKnife;
import cn.pedant.SweetAlert.SweetAlertDialog;

public class Printer extends AppCompatActivity {
    @BindView(R.id.listitem)
    ListView listitem;
    @BindView(R.id.ivadd)
    ImageView ivadd;
    @BindView(R.id.LLempty)
    LinearLayout LLempty;

    String TAG = "Printer";

    BluetoothAdapter bluetoothAdapter;

    ArrayList<Object_bluetooth> listPaired = new ArrayList<>();
    Custom_bluetooth adapterPaired;
    ArrayList<BluetoothDevice> listDevicePaired = new ArrayList<>();

    ArrayList<Object_bluetooth> listAll = new ArrayList<>();
    Custom_bluetooth adapterAll;
    ArrayList<BluetoothDevice> listDeviceAll = new ArrayList<>();

    String resultBluetooth;
    BluetoothSocket mmSocket;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    volatile boolean stopWorker;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition, posx;
    BluetoothDevice mmDevice;

    ActivityResultLauncher<Intent> activityResultLauncher;

    Dialog dialog;
    SweetAlertDialog spin;
    Bitmap image;
    boolean smallSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_printer);
        ButterKnife.bind(this);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        activityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            Log.e(TAG, "Bluetooth On");
        });

        spin = new SweetAlertDialog(Printer.this, SweetAlertDialog.PROGRESS_TYPE);
        spin.setTitleText("Please wait...");

        set_click();
        set_data();

        IntentFilter intent = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(receiverUnpair, intent);
    }

    @Override
    public void onBackPressed() {
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(receiverPair);
            unregisterReceiver(receiverScan);
            unregisterReceiver(receiverUnpair);
        }catch (IllegalArgumentException e){
            e.printStackTrace();
        }
        super.onDestroy();
    }

    private void set_data() {
        Set<BluetoothDevice> bt = bluetoothAdapter.getBondedDevices();
        listDevicePaired.clear();
        listPaired.clear();

        if (bt.size() > 0) {
            for (BluetoothDevice device : bt) {
                listDevicePaired.add(device);
                listPaired.add(new Object_bluetooth(device.getName(), device.getAddress()));
            }
            LLempty.setVisibility(View.GONE);
        } else {
            LLempty.setVisibility(View.VISIBLE);
        }

        adapterPaired = new Custom_bluetooth(this, listPaired);
        listitem.setAdapter(adapterPaired);
        listitem.setOnItemClickListener((parent, view, position, id) -> {
            new AlertDialog.Builder(Printer.this)
                    .setTitle("Select paper size")
                    .setPositiveButton("80mm", (dialogInterface, i) -> {
                        dialogInterface.cancel();
                        smallSize = false;
                        posx = position;
                        new ImageToBitmap(image).execute("https://kgo.googleusercontent.com/profile_vrt_raw_bytes_1587515303_10059.png");
                    })
                    .setNegativeButton("58mm", (dialogInterface, i) -> {
                        dialogInterface.cancel();
                        smallSize = true;
                        posx = position;
                        new ImageToBitmap(image).execute("https://kgo.googleusercontent.com/profile_vrt_raw_bytes_1587515303_10059.png");
                    })
                    .show();
        });
        listitem.setOnItemLongClickListener((parent, view, position, id) -> {
            new AlertDialog.Builder(Printer.this)
                    .setTitle("DELETE PRINTER")
                    .setMessage("Are you sure to delete this printer?")
                    .setPositiveButton("YES", (dialogInterface, i) -> {
                        dialogInterface.cancel();
                        posx = position;
                        unpairDevice(listDevicePaired.get(position));
                    })
                    .setNegativeButton("NO", (dialogInterface, i) -> dialogInterface.cancel())
                    .show();
            return true;
        });
    }

    private void set_click() {
        ivadd.setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(Printer.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(Printer.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(Printer.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 6);
                ActivityCompat.requestPermissions(Printer.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 7);
            } else {
                popup_add_bluetooth();
            }
        });
    }

    class ImageToBitmap extends AsyncTask<String, Void, Bitmap> {
        Bitmap bmImage;

        public ImageToBitmap(Bitmap bmImage) {
            this.bmImage = bmImage;
        }

        @Override
        protected void onPreExecute() {
            // TODO Auto-generated method stub
            super.onPreExecute();
        }

        protected Bitmap doInBackground(String... urls) {
            String urldisplay = urls[0];
            Bitmap mIcon11 = null;
            try {
                InputStream in = new java.net.URL(urldisplay).openStream();
                mIcon11 = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                Log.e("Error", e.getMessage());
                e.printStackTrace();
            }
            return mIcon11;
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            super.onPostExecute(result);
            image = result;
            new PrintBluetooth(Printer.this.getApplicationContext()).execute("");
        }
    }


    @SuppressLint("StaticFieldLeak")
    private class PrintBluetooth extends AsyncTask<String, Integer, String> {
        Context context;

        PrintBluetooth(Context context) {
            this.context = context;
        }

        @SuppressLint("SetTextI18n")
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            spin.show();
        }

        @Override
        protected String doInBackground(String... params) {
            try {
                if (!isBluetoothHeadsetConnected()) {
                    findBT();
                    openBT();
                }
                print();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return "";
        }

        @SuppressLint("SetTextI18n")
        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            spin.cancel();
            try {
                closeBT();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (resultBluetooth.equals("success")) {
                finish();
                overridePendingTransition(0, 0);
            } else if (resultBluetooth.equals("noimage")) {
                new AlertDialog.Builder(Printer.this)
                        .setTitle("Upss.. Sorry")
                        .setMessage("Image not found")
                        .setPositiveButton("OK", (dialogInterface, i) -> dialogInterface.cancel())
                        .show();
            } else {
                new AlertDialog.Builder(Printer.this)
                        .setTitle("Upss.. Sorry")
                        .setMessage("Bluetooth not connected")
                        .setPositiveButton("OK", (dialogInterface, i) -> dialogInterface.cancel())
                        .show();
            }
        }
    }

    protected void print() {
        if (mmSocket == null) {
            new AlertDialog.Builder(Printer.this)
                    .setTitle("Upss.. Sorry")
                    .setMessage("Bluetooth not connected")
                    .setPositiveButton("OK", (dialogInterface, i) -> dialogInterface.cancel())
                    .show();
        } else {
            OutputStream opstream = null;
            try {
                opstream = mmSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mmOutputStream = opstream;

            try {
                mmOutputStream = mmSocket.getOutputStream();

                printLogo(image);
                printText("Print Text Success", 0, 1);

                mmOutputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
                resultBluetooth = "failed";
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void printLogo(Bitmap bitmap) {
        OutputStream opstream = null;
        try {
            opstream = mmSocket.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mmOutputStream = opstream;

        try {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mmOutputStream = mmSocket.getOutputStream();

            byte[] printformat = new byte[]{0x1B, 0x21, 0x03};
            mmOutputStream.write(printformat);

            printPhotoBmp(bitmap);

            mmOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void printPhotoBmp(Bitmap mSaveBit) {
        try {
            Bitmap bmp = mSaveBit;
            if (mSaveBit.getWidth() > 384) {
                bmp = getResizedBitmap(mSaveBit);
            }
            printWebView(bmp);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("PrintTools", "the file isn't exists");
        }
    }

    public Bitmap getResizedBitmap(Bitmap image) {
        int maxSize = 0;
        int width = image.getWidth();
        int height = image.getHeight();
        Log.e("TES", width + " - " + height);
        if (smallSize) {
            maxSize = 340;
        } else {
            maxSize = 540;
        }
        float bitmapRatio = (float) width / (float) height;
        if (bitmapRatio > 1) {
            width = maxSize;
            height = (int) (width / bitmapRatio);
        } else {
            height = maxSize;
            width = (int) (height * bitmapRatio);
        }
        return Bitmap.createScaledBitmap(image, width, height, true);
    }

    private void printWebView(Bitmap bmpScale) {
        try {
            if (bmpScale != null) {
                int rows = bmpScale.getHeight() / 32 + 1;
                mmOutputStream.write(PrinterCommands.ESC_ALIGN_CENTER);
                splitImage(bmpScale, rows, 1);
            } else {
                Log.e("Print Photo error", "the file isn't exists");
            }
        } catch (Exception e) {
            Log.e("ERR", e.getLocalizedMessage());
        }
    }

    private void splitImage(Bitmap bitmap, int rows, int cols) {
        try {
            int chunkHeight = bitmap.getHeight() / rows + 1;
            int chunkWidth = bitmap.getWidth() / cols;

            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.getWidth(), bitmap.getHeight(), true);

            int yCoord = 0;
            for (int x = 0; x < rows; x++) {
                int xCoord = 0;
                for (int y = 0; y < cols; y++) {
                    if (yCoord + chunkHeight > bitmap.getHeight()) {
                        chunkHeight = bitmap.getHeight() - yCoord;
                    }
                    Bitmap bmp = Bitmap.createBitmap(scaledBitmap, xCoord, yCoord, chunkWidth, chunkHeight);
                    if (bmp != null) {
                        byte[] command = Utils.decodeBitmap(bmp);
                        printText(command);
                    } else {
                    }
                    xCoord += chunkWidth;
                }
                yCoord += chunkHeight;
            }
        } catch (Exception e) {
            Log.e("ERR", e.getLocalizedMessage());
        }
    }

    private void printText(String msg, int size, int align) {
        byte[] cc = new byte[]{0x1B, 0x21, 0x00};  // 0- normal size text
        byte[] bb = new byte[]{0x1B, 0x21, 0x08};  // 1- only bold text
        byte[] bb2 = new byte[]{0x1B, 0x21, 0x20}; // 2- bold with medium text
        byte[] bb3 = new byte[]{0x1B, 0x21, 0x10}; // 3- bold with large text
        byte[] xx = new byte[]{0x1B, 0x21, 0x03};  // 4- normal size text
        byte[] xxx = new byte[]{0x1B, 0x21, 0x30};  // 5- normal size text
        try {
            switch (size) {
                case 0:
                    mmOutputStream.write(cc);
                    break;
                case 1:
                    mmOutputStream.write(bb);
                    break;
                case 2:
                    mmOutputStream.write(bb2);
                    break;
                case 3:
                    mmOutputStream.write(bb3);
                    break;
                case 4:
                    mmOutputStream.write(xx);
                    break;
                case 5:
                    mmOutputStream.write(xxx);
                    break;
            }

            switch (align) {
                case 0:
                    mmOutputStream.write(PrinterCommands.ESC_ALIGN_LEFT);
                    break;
                case 1:
                    mmOutputStream.write(PrinterCommands.ESC_ALIGN_CENTER);
                    break;
                case 2:
                    mmOutputStream.write(PrinterCommands.ESC_ALIGN_RIGHT);
                    break;
            }
            mmOutputStream.write(msg.getBytes());
            mmOutputStream.write(PrinterCommands.LF);
            printNewLine();
            printNewLine();
            printNewLine();
            mmOutputStream.write(new byte[]{0x1d, 0x56, 0x42, 0x00}); //cut papper
            mmOutputStream.write(new byte[]{27, 112, 0, 60, 120});// open cash drawer
            resultBluetooth = "success";
        } catch (IOException e) {
            e.printStackTrace();
            resultBluetooth = "failed";
        }
    }

    public void printPhoto(int img) {
        try {
            Bitmap bmp = BitmapFactory.decodeResource(getResources(), img);
            Bitmap b255 = Bitmap.createScaledBitmap(bmp, 255, 255, false);
            if (b255 != null) {
                byte[] command = Utils.decodeBitmap(b255);
                mmOutputStream.write(PrinterCommands.ESC_ALIGN_CENTER);
                printText(command);
            } else {
                resultBluetooth = "noimage";
            }
        } catch (Exception e) {
            e.printStackTrace();
            resultBluetooth = "noimage";
        }
    }

    private void printText(byte[] msg) {
        try {
            mmOutputStream.write(msg);
        } catch (IOException e) {
            e.printStackTrace();
            resultBluetooth = "failed";
        }
    }

    private void printNewLine() {
        try {
            mmOutputStream.write(PrinterCommands.FEED_LINE);
        } catch (IOException e) {
            e.printStackTrace();
            resultBluetooth = "failed";
        }
    }

    public static boolean isBluetoothHeadsetConnected() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled() && mBluetoothAdapter.getProfileConnectionState(BluetoothHeadset.HEADSET) == BluetoothHeadset.STATE_CONNECTED;
    }

    public void findBT() {
        try {
            if (bluetoothAdapter == null) {

            }
            if (!bluetoothAdapter.isEnabled()) {
                Intent intentEnable = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                activityResultLauncher.launch(intentEnable);
            }
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getName().equals(listPaired.get(posx).getName()) || device.getAddress().equals(listPaired.get(posx).getAddress())) {
                        mmDevice = device;
                        break;
                    }
                }
            }

            resultBluetooth = "success";
        } catch (Exception e) {
            e.printStackTrace();
            resultBluetooth = "failed";
        }
    }

    public void openBT() throws IOException {
        try {
            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);

            try {
                mmSocket.connect();
            } catch (IOException e) {
                mmSocket = (BluetoothSocket) mmDevice.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(mmDevice, 1);
                mmSocket.connect();
            }

            mmOutputStream = mmSocket.getOutputStream();
            mmInputStream = mmSocket.getInputStream();
            Thread.sleep(100);

            beginListenForData();

            resultBluetooth = "success";
        } catch (Exception e) {
            e.printStackTrace();
            resultBluetooth = "failed";
        }
    }

    void closeBT() throws IOException {
        try {
            stopWorker = true;
            mmOutputStream.close();
            mmInputStream.close();
            mmSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void beginListenForData() {
        try {
            final byte delimiter = 10;

            stopWorker = false;
            readBufferPosition = 0;
            readBuffer = new byte[1024];

            workerThread = new Thread(() -> {

                while (!Thread.currentThread().isInterrupted() && !stopWorker) {

                    try {

                        int bytesAvailable = mmInputStream.available();

                        if (bytesAvailable > 0) {

                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);

                            for (int i = 0; i < bytesAvailable; i++) {

                                byte b = packetBytes[i];
                                if (b == delimiter) {

                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(
                                            readBuffer, 0,
                                            encodedBytes, 0,
                                            encodedBytes.length
                                    );

                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }

                    } catch (IOException ex) {
                        stopWorker = true;
                    }

                }
            });

            workerThread.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    BroadcastReceiver receiverScan = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if (!listDeviceAll.contains(device) && !String.valueOf(device.getName()).equals("null")) {
                    listDeviceAll.add(device);
                    listAll.add(new Object_bluetooth(device.getName(), device.getAddress()));
                    adapterAll.notifyDataSetChanged();
                }
            }
        }
    };

    BroadcastReceiver receiverPair = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                final int prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);

                if (state == BluetoothDevice.BOND_BONDED && prevState == BluetoothDevice.BOND_BONDING) {
                    Toast.makeText(Printer.this, "Connected", Toast.LENGTH_SHORT).show();
                    dialog.cancel();

                    Set<BluetoothDevice> bt = bluetoothAdapter.getBondedDevices();
                    listDevicePaired.clear();
                    listPaired.clear();

                    if (bt.size() > 0) {
                        for (BluetoothDevice device : bt) {
                            listDevicePaired.add(device);
                            listPaired.add(new Object_bluetooth(device.getName(), device.getAddress()));
                        }
                        LLempty.setVisibility(View.GONE);
                    } else {
                        LLempty.setVisibility(View.VISIBLE);
                    }
                    adapterPaired.notifyDataSetChanged();
                }
            }
        }
    };

    BroadcastReceiver receiverUnpair = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                final int prevState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);

                if (state == BluetoothDevice.BOND_NONE && prevState == BluetoothDevice.BOND_BONDED) {
                    Toast.makeText(Printer.this, "Deleted", Toast.LENGTH_SHORT).show();

                    Set<BluetoothDevice> bt = bluetoothAdapter.getBondedDevices();
                    listDevicePaired.clear();
                    listPaired.clear();

                    if (bt.size() > 0) {
                        for (BluetoothDevice device : bt) {
                            listDevicePaired.add(device);
                            listPaired.add(new Object_bluetooth(device.getName(), device.getAddress()));
                        }
                        LLempty.setVisibility(View.GONE);
                    } else {
                        LLempty.setVisibility(View.VISIBLE);
                    }
                    adapterPaired.notifyDataSetChanged();
                }
            }
        }
    };

    private void pairDevice(BluetoothDevice device) {
        try {
            Method method = device.getClass().getMethod("createBond", (Class[]) null);
            method.invoke(device, (Object[]) null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void unpairDevice(BluetoothDevice device) {
        try {
            Method method = device.getClass().getMethod("removeBond", (Class[]) null);
            method.invoke(device, (Object[]) null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //POPUP=========================================================================================
    private void popup_add_bluetooth() {
        dialog = new Dialog(Printer.this, R.style.Theme_AppCompat_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.popup_add_bluetooth);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        dialog.show();

        ImageView ivback = dialog.findViewById(R.id.ivback);
        ListView listitem = dialog.findViewById(R.id.listitem);

        ivback.setOnClickListener(v -> dialog.cancel());

        adapterAll = new Custom_bluetooth(this, listAll);
        listitem.setAdapter(adapterAll);
        listitem.setOnItemClickListener((parent, view, position, id) -> {
            if (listDeviceAll.get(position).getBondState() == 10) {
                pairDevice(listDeviceAll.get(position));
            } else if (listDeviceAll.get(position).getBondState() == 12) {
                Toast.makeText(Printer.this, "Already Connected", Toast.LENGTH_SHORT).show();
                dialog.cancel();
            }
        });

        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiverScan, intentFilter);

        IntentFilter intent = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(receiverPair, intent);

        bluetoothAdapter.startDiscovery();
    }
    //POPUP=========================================================================================

}