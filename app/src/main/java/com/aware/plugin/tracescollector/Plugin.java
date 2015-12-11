package com.aware.plugin.tracescollector;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.utils.Aware_Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.UUID;

public class Plugin extends Aware_Plugin {

    public static final String ACTION_AWARE_PLUGIN_TRACES_CONNECTION_START = "ACTION_AWARE_PLUGIN_TRACES_CONNECTION_START";
    public static final String ACTION_AWARE_PLUGIN_TRACES_CONNECTION_VERIFY = "ACTION_AWARE_PLUGIN_TRACES_CONNECTION_VERIFY";

    public static final int MESSAGE_READ = 1837;

    public static final String NOTI_TYPE = "TYPE";

    public static final String BT_OFF = "BT_OFF";

    public static final String DISCONNECTED = "DISCONNECTED";

    public static final String BT_DEVICE_ID = "bt_device_id";

    public static final String CHECK_THREAD = "check_thread";

    public static final int NOTIFICATION_ID = 43263;

    private static String uuid;

    private static BluetoothAdapter mBluetoothAdapter;

    private static ConnectedThread conn;
    private static MyHandler mHandler;

    private static ContextProducer context_producer;

    private static ServerAcceptThread serverAcceptThread;

    public static Plugin plugin;

    public static class ConnectionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if( intent.getAction().equals(ACTION_AWARE_PLUGIN_TRACES_CONNECTION_START) ) {
                startConnection(context);
            }
            if( intent.getAction().equals(ACTION_AWARE_PLUGIN_TRACES_CONNECTION_VERIFY) ) {
                Intent connected = new Intent(context, HelperActivity.class);
                connected.setAction(HelperActivity.EXTRA_CONNECTION_STATUS);
                connected.putExtra("ACTION", HelperActivity.EXTRA_CONNECTION_STATUS);
                connected.putExtra(HelperActivity.EXTRA_CONNECTION_STATUS, connected());
                connected.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(connected);
            }
        }
    }

    @Override
    public void onCreate() {
        plugin = this;
        super.onCreate();
        TAG = "AWARE::"+getResources().getString(R.string.app_name);
        DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

        if( DEBUG ) Log.d(TAG, "Traces Collector plugin running");
        //Initialize our plugin's settings
        if( Aware.getSetting(this, Settings.STATUS_PLUGIN_TRACESCOLLECTOR).length() == 0 ) {
            Aware.setSetting(this, Settings.STATUS_PLUGIN_TRACESCOLLECTOR, true);
        }

        if(Aware.getSetting(this, Settings.BT_UUID_KEY).length() == 0)
        {
            uuid = "cfa37877-e7a1-41a8-9673-2b0844b5868f";
        }
        else
        {
            uuid = Aware.getSetting(this, Settings.BT_UUID_KEY);
            Log.d("UUID", "Got UUID from AWARE settings");
        }

        //Activate any sensors/plugins you need here
        //...
        serverAcceptThread = new ServerAcceptThread(getApplicationContext(), this);

        //Any active plugin/sensor shares its overall context using broadcasts
        CONTEXT_PRODUCER = new ContextProducer() {
            @Override
            public void onContext() {
                //Broadcast your context here
                //TODO: Broadcast values of trace
            }
        };
        context_producer = CONTEXT_PRODUCER;

        REQUIRED_PERMISSIONS.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        REQUIRED_PERMISSIONS.add(Manifest.permission.BLUETOOTH);
        REQUIRED_PERMISSIONS.add(Manifest.permission.BLUETOOTH_ADMIN);
        REQUIRED_PERMISSIONS.add(Manifest.permission.INTERNET);
        REQUIRED_PERMISSIONS.add(Manifest.permission.ACCESS_NETWORK_STATE);


        //To sync data to the server, you'll need to set this variables from your ContentProvider
        DATABASE_TABLES = Provider.DATABASE_TABLES;
        TABLES_FIELDS = Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{ Provider.TracesCollector_Data.CONTENT_URI };

        sendNotification(getApplicationContext());
        mHandler = new MyHandler(this);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(getApplicationContext())
                        .setSmallIcon(R.drawable.icon_remote_white)
                        .setContentTitle("Traces Collector");
        startForeground(NOTIFICATION_ID, mBuilder.build());
        Aware.startPlugin(this, "com.aware.plugin.tracescollector");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent!=null && intent.getAction()!= null && intent.getAction().equals(CHECK_THREAD))
        {
            if(serverAcceptThread != null)
            {
                try{
                    serverAcceptThread.cancel();
                    serverAcceptThread.interrupt();
                    if(conn !=null)
                    {
                        try{
                            conn.interrupt();
                            conn.cancel();
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                    }
                }catch(Exception e){
                    e.printStackTrace();
                    Log.d("TCError","Error canceling thread");
                }
            }
            try{
                serverAcceptThread = new ServerAcceptThread(getApplicationContext(), this);
                serverAcceptThread.start();
            }catch (Exception e){
                e.printStackTrace();
                Log.d("TCError","Error starting thread");
            }
        }
        else
        {
//            super.onStartCommand(intent, flags, startId);

            //This function gets called every 5 minutes by AWARE to make sure this plugin is still running.
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

            sendNotification(getApplicationContext());
        }
        return super.onStartCommand(intent, flags, startId);
    }

    public static Plugin getPlugin()
    {
        if(plugin == null)
        {
            plugin = new Plugin();
        }
        return plugin;
    }

    private void sendNotification(Context c)
    {
        if(!connected())
        {
            Intent notificationIntent = new Intent(HelperActivity.NOTIFICATION_ACTION);

            try{
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
                    Log.d("BT Connection","Bluetooth is not enabled Noti");
                    notificationIntent.putExtra(NOTI_TYPE,BT_OFF);
                }
                else
                {
                    notificationIntent.putExtra(NOTI_TYPE,DISCONNECTED);
                    if(serverAcceptThread != null)
                    {
                        try{
                            serverAcceptThread.cancel();
                            serverAcceptThread.interrupt();
                            if(conn !=null)
                            {
                                try{
                                    conn.interrupt();
                                    conn.cancel();
                                }
                                catch (Exception e)
                                {
                                    e.printStackTrace();
                                }
                            }
                        }catch(Exception e){
                            e.printStackTrace();
                            Log.d("TCError","Error canceling thread");
                        }
                    }
                    try{
                        serverAcceptThread = new ServerAcceptThread(c, this);
                        serverAcceptThread.start();
                    }catch (Exception e){
                        e.printStackTrace();
                        Log.d("TCError","Error starting thread");
                    }
                }

            }catch(Exception e){
                e.printStackTrace();
            }

            sendOrderedBroadcast(notificationIntent, null);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if( DEBUG ) Log.d(TAG, "Traces collector plugin terminated");
        Aware.setSetting(this, Settings.STATUS_PLUGIN_TRACESCOLLECTOR, false);

        //Deactivate any sensors/plugins you activated here
        //...
        try{
            serverAcceptThread.interrupt();
            serverAcceptThread.cancel();
            if(conn !=null)
            {
                try{
                    conn.interrupt();
                    conn.cancel();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }

        NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        int mNotificationId = 13548;

        mNotifyMgr.cancel(mNotificationId);

        //Ask AWARE to apply your settings
        //sendBroadcast(new Intent(Aware.ACTION_AWARE_REFRESH));
        Aware.stopPlugin(this, "com.aware.plugin.tracescollector");
    }

    private static class ServerAcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;
        private Context sContext;
        private boolean ready;
        private Plugin plugin;

        public ServerAcceptThread(Context c, Plugin activity) {
            this.sContext = c;
            plugin = activity;
            ready = false;
            // Use a temporary object that is later assigned to mmServerSocket,
            // because mmServerSocket is final
            BluetoothServerSocket tmp = null;
            Log.d("TCError","Thread created");
            try {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                // MY_UUID is the app's UUID string, also used by the client code
                if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
                    activity.sendNotification(c);
                    ready = false;
                }
                else
                {
                    tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("test", UUID.fromString(uuid));
                    ready = true;
                }
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("TCError","Error listening");}
            mmServerSocket = tmp;
        }

        public void run() {
            if(ready)
            {

                BluetoothSocket socket = null;
                // Keep listening until exception occurs or a socket is returned
                while (true) {
                    try {
                        Log.d("TCError","starting to accept");
                        socket = mmServerSocket.accept();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.d("TCError","Error accepting IO");
                        break;
                    } catch (Exception e)
                    {
                        e.printStackTrace();
                        Log.d("TCError","Error accepting G");
                        break;
                    }
                    // If a connection was accepted
                    if (socket != null) {
                        // Do work to manage the connection (in a separate thread)
                        storeDevice(sContext, socket);
                        manageConnectedSocket(sContext, socket);

                        try{
                            mmServerSocket.close();

                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.d("TCError","Error closing ssocket");
                        }
                        break;
                    }
                }
            }
            else
            {
                plugin.sendNotification(sContext);
            }
        }

        /** Will cancel the listening socket, and cause the thread to finish */
        public void cancel() {
            try {
                if(mmServerSocket!=null)
                    mmServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("TCError","Error closing socket IO");}
        }
    }

    private static void storeDevice(Context c, BluetoothSocket socket)
    {

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(c.getApplicationContext())
                        .setSmallIcon(R.drawable.icon_remote_white)
                        .setContentTitle("Traces Collector")
                        .setContentText("Bluetooth connection stable");
        // Sets an ID for the notification

        // Gets an instance of the NotificationManager service

        // Builds the notification and issues it.
        NotificationManager mNotifyMgr =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotifyMgr.notify(Plugin.NOTIFICATION_ID, mBuilder.build());

        SharedPreferences sharedPreferences = c.getSharedPreferences(c.getPackageName(), MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putString(BT_DEVICE_ID, socket.getRemoteDevice().getAddress());
        editor.commit();
    }

    private static void manageConnectedSocket(Context c, BluetoothSocket socket)
    {
        NotificationManager mNotifyMgr =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);

        int mNotificationId = 13548;

        mNotifyMgr.cancel(mNotificationId);
        conn = Plugin.getConnectedThread(socket);

        conn.start();

        //Manage connection
    }

    private static class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                if(socket != null)
                {
                    tmpIn = socket.getInputStream();
                    tmpOut = socket.getOutputStream();
                }
                else{
                    this.cancel();
                    conn = null;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);

                    // Send the obtained bytes to the UI activity
                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                    this.cancel();
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
                mmOutStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            if(mmSocket != null)
                try {

                    mmSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            if(mmInStream != null)
                try {
                    mmInStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            if(mmOutStream != null)
                try {
                    mmOutStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            conn = null;
        }

        @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        public boolean isConnected()
        {
            try {
                mmOutStream.write("alive".getBytes());
                mmOutStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            return mmSocket.isConnected();
        }
    }

    private static class MyHandler extends Handler {
        private final WeakReference<Plugin> mPlugin;

        public MyHandler(Plugin plugin) {
            mPlugin = new WeakReference<Plugin>(plugin);
        }

        @Override
        public void handleMessage(Message msg) {
            Plugin plugin = mPlugin.get();
            if (plugin != null) {
                switch (msg.what) {
                    case MESSAGE_READ:
                        byte[] data = (byte[]) msg.obj;
                        String readMessage = new String((byte[]) msg.obj, 0, (int) msg.arg1).trim();
                        Log.d("Connection", readMessage);

                        if (readMessage != null) {
                            int count = readMessage.length() - readMessage.replace("#", "").length();
                            if (count == 4 && readMessage.startsWith("#") && readMessage.endsWith("#")) {
                                String[] information = readMessage.split("#", -1);
                                for (int i = 1; i < 4; i++) {
                                    Log.d("To Store", "TAG" + i + ": " + information[i]);
                                }

                                storeData(information[1], information[2], information[3], plugin);
                            }
                        }
                }
            }
        }
    }

//    @Override
//    public IBinder onBind(Intent intent) {
//        startService(new Intent(this, Plugin.class));
//        return mBinder;
//    }

    //returns the instance of the service
//    public class LocalBinder extends Binder {
//        public Plugin getServiceInstance(){
//            return Plugin.this;
//        }
//    }

    //Here Activity register to the service as Callbacks client
//    public void registerClient(Activity activity){
//        this.activity = (Callbacks)activity;
//    }

    //callbacks interface for communication with service clients!
    public interface Callbacks{
        public void updateClient(long data);
    }

    public static void startConnection(Context c)
    {
        try{
            if(serverAcceptThread != null)
            {
                serverAcceptThread.cancel();
                serverAcceptThread.interrupt();
                if(conn !=null)
                {
                    try{
                        conn.interrupt();
                        conn.cancel();
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }catch(Exception e){
            e.printStackTrace();
            Log.d("TCError","Error canceling thread force");
        }
        try{
            serverAcceptThread = new ServerAcceptThread(c, Plugin.getPlugin());
            serverAcceptThread.start();
        }catch (Exception e){
            e.printStackTrace();
            Log.d("TCError","Error starting thread force");
        }
    }

    public static boolean connected()
    {
        if(conn != null)
        {
            return conn.isConnected();
        }
        return false;
    }

    private static void storeData(String tag_1, String tag_2, String tag_3, Context context)
    {
        ContentValues data = new ContentValues();
        data.put(Provider.TracesCollector_Data.TIMESTAMP, System.currentTimeMillis());
        data.put(Provider.TracesCollector_Data.DEVICE_ID, Aware.getSetting(context.getApplicationContext(), Aware_Preferences.DEVICE_ID));
        data.put(Provider.TracesCollector_Data.TAG_1, tag_1);
        data.put(Provider.TracesCollector_Data.TAG_2, tag_2);
        data.put(Provider.TracesCollector_Data.TAG_3, tag_3);

        context.getContentResolver().insert(Provider.TracesCollector_Data.CONTENT_URI, data);

        context_producer.onContext();
    }

    //Create and register receiver
    public static class BluetoothDisconnectReceiver extends BroadcastReceiver{

        private BluetoothAdapter mBluetoothAdapter;
        private Context context;
        @Override
        public void onReceive(Context context, Intent intent) {
            this.context = context;
            String action = intent.getAction();
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            SharedPreferences sharedPreferences = context.getSharedPreferences(context.getPackageName(), MODE_PRIVATE);
            String bt_address = sharedPreferences.getString(BT_DEVICE_ID,"");
            if(isMyServiceRunning(Plugin.class))
            {
                switch(action){
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                        if(device.getAddress().equals(bt_address))
                        {
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString(BT_DEVICE_ID,"");
                            editor.commit();
                            Intent notificationIntent = new Intent(HelperActivity.NOTIFICATION_ACTION);

                            try{
                                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                                if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
                                    Log.d("BT Connection","Bluetooth is not enabled");
                                    notificationIntent.putExtra(NOTI_TYPE,BT_OFF);
                                }
                                else
                                {
                                    notificationIntent.putExtra(NOTI_TYPE,DISCONNECTED);
                                    Intent restartIntent = new Intent(context, Plugin.class);
                                    restartIntent.setAction(CHECK_THREAD);

                                    context.startService(restartIntent);
                                }

                            }catch(Exception e){
                                e.printStackTrace();
                            }

                            context.sendOrderedBroadcast(notificationIntent, null);
                        }
                        break;
                    case BluetoothAdapter.ACTION_STATE_CHANGED:
                        int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                        switch(state)
                        {
                            case BluetoothAdapter.STATE_OFF:
                            case BluetoothAdapter.STATE_TURNING_OFF:
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.putString(BT_DEVICE_ID,"");
                                editor.commit();
                                Intent notificationIntent = new Intent(HelperActivity.NOTIFICATION_ACTION);

                                Log.d("BT Connection","Bluetooth is not enabled");
                                notificationIntent.putExtra(NOTI_TYPE, BT_OFF);

                                context.sendOrderedBroadcast(notificationIntent, null);
                                break;
                        }
                        break;
                }

            }

        }

        private boolean isMyServiceRunning(Class<?> serviceClass) {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
            return false;
        }
    }

    public static ConnectedThread getConnectedThread(BluetoothSocket socket)
    {
        if(conn == null)
        {
            conn = new ConnectedThread(socket);
        }
        return conn;
    }
}
