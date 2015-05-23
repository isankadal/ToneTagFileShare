package com.tonetag.p2p;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.tonetag.tone.SoundPlayer;
import com.tonetag.tone.SoundRecorder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;


public class MainActivity extends Activity implements SoundRecorder.OnDataFoundListener, SoundPlayer.OnPlaybackFinishedListener{
    private ImageView iv_image;
    private TextView tv_file_path;
    private WifiP2pManager mManager;
    private SoundPlayer mSoundPlayer;
    private SoundRecorder mSoundRecorder;
    private WifiP2pManager.Channel mChannel;
    private Bitmap bitmap;
    private WifiManager wm;
    private WifiInfo wInfo;
    private Context context;
    private static final String TAG = "p2p";
    private boolean server = false;
    private String picturePath;
    private String clientMAC;
    private IntentFilter mIntentFilter;


    private static final int REQUEST_CODE = 1;

    private static final int PORT = 8888;

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this.getApplicationContext();
        mSoundPlayer = new SoundPlayer(context);
        mSoundPlayer.setOnPlaybackFinishedListener(this);
        mSoundRecorder = new SoundRecorder(context);
        mSoundRecorder.setOnDataFoundListener(this);


        tv_file_path = (TextView) findViewById(R.id.tv_file_path);
        iv_image = (ImageView) findViewById(R.id.iv_image);

        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
        wm = (WifiManager) getSystemService(WIFI_SERVICE);
        wInfo = wm.getConnectionInfo();

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

    }

    public void onClick(View view){
        switch (view.getId()){
            case R.id.btn_send:
                //To-Do
                //select file path and display image
                // make ready as client
                // start broadcasting
                server = false;

                loadImage();
                break;

            case R.id.btn_receive:
                //To-Do
                // Turn on wifi direct as server
                // laod the input-stream as to display in view
                server = true;

                startReceiver();

                //getP2PDevice("");

                break;
        }
    }

    private void startReceiver(){
        //String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());

        //Log.e(TAG, ip);

        mSoundRecorder.startRecording(SoundRecorder.STRING_RECORD_MODE, SoundRecorder.SENDER_CUSTOMER, true, 3000, 10);

        //mSoundPlayer.setDeviceVolume(10);
        //mSoundPlayer.sendString(ip);

    }

    private void loadImage(){
        Intent i = new Intent(
                Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        startActivityForResult(i, REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            Uri selectedImage = data.getData();
            String[] filePathColumn = {MediaStore.Images.Media.DATA};

            Cursor cursor = getContentResolver().query(selectedImage,
                    filePathColumn, null, null, null);
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            picturePath = cursor.getString(columnIndex);
            cursor.close();
            tv_file_path.setText(picturePath);

            iv_image.setImageBitmap(BitmapFactory.decodeFile(picturePath));

            String macAddress = wInfo.getMacAddress();

            mSoundPlayer.setDeviceVolume(10);
            mSoundPlayer.sendString(macAddress);
        }
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private WifiP2pDevice getP2PDevice(String deviceAddress){
        Log.e(TAG, "clientMAC : " +clientMAC);
        WifiP2pDevice p2pDevice = new WifiP2pDevice();
        p2pDevice.deviceAddress = deviceAddress;
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = clientMAC;//p2pDevice.deviceAddress;
        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                //success logic
                new FileServerAsyncTask(context, tv_file_path).execute();
                Log.d(TAG, "Connection Success");
            }

            @Override
            public void onFailure(int reason) {
                //failure logic
                Log.d(TAG, "failed to connect " + reason);
            }
        });

        return p2pDevice;
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPlayers();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPlayers();
    }

    private void stopPlayers(){
        if(mSoundPlayer != null){
            mSoundPlayer.stop();
        }
        if(mSoundRecorder.isRecordingOn()){
            mSoundRecorder.stopRecording();
        }
    }

    @Override
    public void onDataFound(final String s, int i, boolean b, short i2) {
        if (server){
            clientMAC = s;
            String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
            try {
                Thread.sleep(500l);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Log.e(TAG, "mac : "+ s);

            //Log.e(TAG, ip);
            mSoundPlayer.setDeviceVolume(10);
            mSoundPlayer.sendString(ip);
            //getP2PDevice(s);
        }else{
            Looper.prepareMainLooper();
            new FileClientAsyncTask(s).execute(" ");

        }
    }

    @Override
    public void onDataFound(long l, int i, short i2) {

    }

    @Override
    public void onPlaybackFinished() {

        if(server){
            //String macAddress = wInfo.getMacAddress();
            //Log.e(TAG, "mac :"+macAddress);
            //getP2PDevice(macAddress);
            getP2PDevice(clientMAC);
            //mSoundRecorder.startRecording(SoundRecorder.STRING_RECORD_MODE, SoundRecorder.SENDER_CUSTOMER, true, 3000, 10);
        }else{
            mSoundRecorder.startRecording(SoundRecorder.STRING_RECORD_MODE, SoundRecorder.SENDER_CUSTOMER, true, 3000, 10);
        }

    }

    private class FileClientAsyncTask extends AsyncTask<String, Void, Void>{
        String host;
        Socket socket = new Socket();

        FileClientAsyncTask(String host){
            this.host = host;
            Log.e("ini", "thread");
        }


        @Override
        protected Void doInBackground(String... params) {
            try {
                /**
                 * Create a client socket with the host,
                 * port, and timeout information.
                 */
                socket.bind(null);
                socket.connect((new InetSocketAddress(host, PORT)), 500);

                /**
                 * Create a byte stream from a JPEG file and pipe it to the output stream
                 * of the socket. This data will be retrieved by the server device.
                 */
                OutputStream outputStream = socket.getOutputStream();
                ContentResolver cr = context.getContentResolver();
                InputStream inputStream = null;
                inputStream = cr.openInputStream(Uri.parse(picturePath));
                byte buf[]  = new byte[1024];
                int len = -1;
                while ((len = inputStream.read(buf)) != -1) {
                    outputStream.write(buf, 0, len);
                }
                outputStream.close();
                inputStream.close();
            } catch (FileNotFoundException e) {
                //catch logic
            } catch (IOException e) {
                //catch logic
            }

/**
 * Clean up any open sockets when done
 * transferring or if an exception occurred.
 */
            finally {
                if (socket != null) {
                    if (socket.isConnected()) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            //catch logic
                        }
                    }
                }
            }


            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Log.e(TAG, "done");
        }
    }

    private class FileServerAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;
        private TextView statusText;
        private File f;

        public FileServerAsyncTask(Context context, View statusText) {
            this.context = context;
            this.statusText = (TextView) statusText;
        }

        @Override
        protected String doInBackground(Void... params) {
            try {

                /**
                 * Create a server socket and wait for client connections. This
                 * call blocks until a connection is accepted from a client
                 */
                ServerSocket serverSocket = new ServerSocket(PORT);
                Socket client = serverSocket.accept();

                /**
                 * If this code is reached, a client has connected and transferred data
                 * Save the input stream from the client as a JPEG file
                 */
                 f = new File(Environment.getExternalStorageDirectory() + "/"
                        + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                        + ".jpg");

                File dirs = new File(f.getParent());
                if (!dirs.exists())
                    dirs.mkdirs();
                f.createNewFile();
                FileOutputStream fos = new FileOutputStream(f);

                InputStream inputstream = client.getInputStream();
                //copyFile(inputstream, new FileOutputStream(f));
                int read = -1;
                byte[] buffer = new byte[1024];
                while((read = inputstream.read(buffer))!= -1){
                    fos.write(buffer);
                }
                serverSocket.close();
                return f.getAbsolutePath();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                return null;
            }
        }

        /**
         * Start activity that can handle the JPEG image
         */
        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                statusText.setText("File copied - " + result);

                //iv_image.setImageBitmap();

                /*Intent intent = new Intent();
                intent.setAction(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse("file://" + result), "image*//*");
                context.startActivity(intent);*/
            }
        }

    }

}
