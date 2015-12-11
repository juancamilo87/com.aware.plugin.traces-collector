package com.aware.plugin.tracescollector;

import android.app.Activity;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * Created by researcher on 03/06/15.
 */
public class HelperActivity extends Activity implements Plugin.Callbacks{

    public static final int TIME_DISCOVERABLE = 120;
    public static final int DISCOVERABLE_REQUEST = 4;  // The request code
    public static final String NOTIFICATION_ID = "notification_id";
    public static final String NOTIFICATION_ACTION = "com.aware.plugin.tracescollector.notification";
    public static final String ACTION_FROM_NOTIFICATION = "com.aware.plugin.tracescollector.notification.action";
    public static final String EXTRA_CONNECTION_STATUS = "extra_connected";

    private Intent serviceIntent;

    private ImageView led;
    private Button btn_connect;
    private Button btn_close;

    private Handler handler;

    private Runnable runnable;

    private boolean connecting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.helper_activity);
        connecting = false;

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.cancel(getIntent().getIntExtra(NOTIFICATION_ID, -1));

        led = (ImageView) findViewById(R.id.helper_led);
        btn_connect = (Button) findViewById(R.id.helper_connect);
        btn_close = (Button) findViewById(R.id.helper_close);


        btn_connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connecting = true;
                Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, TIME_DISCOVERABLE);

                startActivityForResult(discoverableIntent, DISCOVERABLE_REQUEST);
            }
        });

        btn_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        btn_connect.setEnabled(false);
        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                verifyConnected();
                handler.postDelayed(this,1000);
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
//        if( getIntent()!=null &&  getIntent().getStringExtra("ACTION")!=null &&
//                getIntent().getStringExtra("ACTION").equals(EXTRA_CONNECTION_STATUS) ) {
//            Log.d("Whatever", "Result of verification");
//            updateUI(getIntent().getBooleanExtra(EXTRA_CONNECTION_STATUS, false));
//        }
        if(getIntent()!=null &&  getIntent().getStringExtra("ACTION")!=null &&
                getIntent().getStringExtra("ACTION").equals("Bluetooth"))
        {
            if(!connecting)
            {
                connecting = true;
                Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, TIME_DISCOVERABLE);

                startActivityForResult(discoverableIntent, DISCOVERABLE_REQUEST);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if( intent!=null &&  intent.getStringExtra("ACTION")!=null &&
                intent.getStringExtra("ACTION").equals(EXTRA_CONNECTION_STATUS) ) {
            updateUI(intent.getBooleanExtra(EXTRA_CONNECTION_STATUS, false));
        }
//        else if(intent!=null &&  intent.getStringExtra("ACTION")!=null &&
//                intent.getStringExtra("ACTION").equals("Bluetooth"))
//        {
//            connecting = true;
//            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
//            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, TIME_DISCOVERABLE);
//
//            startActivityForResult(discoverableIntent, DISCOVERABLE_REQUEST);
//        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to

        if(requestCode == DISCOVERABLE_REQUEST)
        {
            if(resultCode == TIME_DISCOVERABLE)
            {
                connecting = true;
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        connecting = false;
                    }
                }, TIME_DISCOVERABLE * 1000);
                Toast.makeText(this, "Made discoverable",Toast.LENGTH_SHORT).show();
//                plugin.startConnection();
//                finish();
                NotificationCompat.Builder mBuilder =
                        new NotificationCompat.Builder(getApplicationContext())
                                .setSmallIcon(R.drawable.icon_remote_white)
                                .setContentTitle("Traces Collector")
                                .setContentText("Bluetooth on");
                // Sets an ID for the notification

                // Gets an instance of the NotificationManager service

                // Builds the notification and issues it.
                NotificationManager mNotifyMgr =
                        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                mNotifyMgr.notify(Plugin.NOTIFICATION_ID, mBuilder.build());
                Intent intent = new Intent(Plugin.ACTION_AWARE_PLUGIN_TRACES_CONNECTION_START);
                sendBroadcast(intent);

            }
            if(resultCode == RESULT_CANCELED)
            {
                connecting = false;
                Toast.makeText(this, "Please set the device discoverable",Toast.LENGTH_SHORT).show();
            }
        }

    }

    @Override
    public void updateClient(long data) {

    }

    @Override
    protected void onResume() {
        super.onResume();

        handler.removeCallbacks(runnable);
        handler.postDelayed(runnable, 1000);

        IntentFilter filter = new IntentFilter();
        filter.addAction(NOTIFICATION_ACTION);
        filter.setPriority(2);
        registerReceiver(receiver, filter);

    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(runnable);
        try{
            unregisterReceiver(receiver);
        } catch (Exception e){}
    }

    private void verifyConnected()
    {
        Intent intent = new Intent(Plugin.ACTION_AWARE_PLUGIN_TRACES_CONNECTION_VERIFY);
        sendBroadcast(intent);
    }

    private void updateUI(boolean connected)
    {
        btn_connect.setEnabled(true);
        if(connected)
        {
            connecting = false;
            led.setImageResource(R.drawable.on_led);
            btn_connect.setVisibility(View.GONE);
            btn_close.setVisibility(View.VISIBLE);
            btn_connect.setText("Connect");
        }
        else
        {
            led.setImageResource(R.drawable.off_led);
            btn_connect.setVisibility(View.VISIBLE);
            btn_close.setVisibility(View.GONE);
            if(connecting)
            {
                btn_connect.setText("Connecting...");
                btn_connect.setEnabled(false);
            }
            else
            {
                btn_connect.setText("Connect");
                btn_connect.setEnabled(true);
            }

        }
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            NotificationCompat.Builder mBuilder =
                    new NotificationCompat.Builder(context.getApplicationContext())
                            .setSmallIcon(R.drawable.icon_remote_white)
                            .setContentTitle("Traces Collector")
                            .setContentText("Your connection got lost. Please connect again.");
            // Sets an ID for the notification

            // Gets an instance of the NotificationManager service
            NotificationManager mNotifyMgr =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            // Builds the notification and issues it.
            mNotifyMgr.notify(Plugin.NOTIFICATION_ID, mBuilder.build());
            abortBroadcast();
        }
    };
}
