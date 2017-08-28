package com.example.bluetoothserver;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.os.*;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;


public class ServerActivity extends AppCompatActivity {


    // Application UUID to look for during connection, may be configurable in future
    private final UUID uuid = UUID.fromString("1cb5d5ce-00f5-11e7-93ae-92361f002671");

    // Messages, for when any event happens, to be sent to the main thread
    public final int MESSAGE_INPUT = 0;
    public final int MESSAGE_TOAST = 1;
    public final int MESSAGE_DISCONNECTED = 2;
    public final int MESSAGE_CONNECTED = 3;

    // Whether bluetooth hardware setup failed, such as nonexistent bluetoothdevice
    private boolean bluetoothFailed = false;

    // Message Handler, simple!
    Handler m_handler;

    // Simple log function
    protected void l(String s)
    {
        Log.d(TAG, s);
    }

    // The log tag
    public static final String TAG = "BLUETOOTH_SCOUTER_DEBUG";

    // Send a specific message, from the above list
    public synchronized void MSG(int msg) { m_handler.obtainMessage(msg, 0, -1, 0).sendToTarget(); }

    // Number of active and allowed devices
    private int allowedDevices = 6;

    // Bluetooth hardware adapter
    protected BluetoothAdapter m_bluetoothAdapter;


    // Display a popup box (not a toast, LOL)
    protected void toast(String text)
    {
        try {
            AlertDialog.Builder dlg = new AlertDialog.Builder(this);
            dlg.setTitle("");
            dlg.setMessage(text);
            dlg.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            dlg.setCancelable(true);
            dlg.create();
            dlg.show();
        }
        catch (Exception e)
        {
            l("Failed to create dialog: " + e.getMessage());
        }
    }

    TextView connectedDevicesText;
    EditText serverLogText;

    // When the app is initialized, setup the UI and the bluetooth adapter
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);
        m_handler = new Handler() {
            @Override
            public void handleMessage(Message msg)
            {
                handle(msg);
            }
        };

        setupBluetooth();

        connectedDevicesText = (TextView) findViewById(R.id.connectedDevicesText);
        serverLogText = (EditText) findViewById(R.id.serverLog);
        // serverLogText.setInputType(InputType.TYPE_NULL);
    }

    // Log to the end user about things like connected and disconnected devices
    private void VisualLog(String text)
    {
        serverLogText.append(text);
    }

    // Handle an input message from one of the bluetooth threads
    protected synchronized void handle(Message msg)
    {
        switch (msg.what)
        {
            case MESSAGE_INPUT:

                byte[] info = (byte[]) msg.obj;
                String message = new String(info);
                //m_sendButton.setText(message);

                break;
            case MESSAGE_TOAST:
                String t = new String((byte[]) msg.obj);

                l("TOASTING: " + t);

                //toast(t);

                break;
            case MESSAGE_CONNECTED:
                l("Received Connect");
                connectedDevicesText.setText(String.valueOf(connectedThreads.size()));
                break;
            case MESSAGE_DISCONNECTED:
                //toast("DISCONNECTED FROM DEVICE");
                l("Received Disconnect");
                connectedDevicesText.setText(String.valueOf(connectedThreads.size()));
                break;
        }
    }

    // Initialize the accept bluetooth connections thread
    private void setupThreads()
    {
        if (!bluetoothFailed) {
            l("Setting up accept thread");
            acceptThread = new AcceptThread();

            l("Running accept thread");
            acceptThread.start();
        }
        else
            l("Attempted to setup threads, but bluetooth setup has failed");
    }

    //
    // Send a message under MESSAGE_TOAST,
    // not utilized atm,
    // but may be useful for sending info to main thread about other things
    //
    private void msgToast(String msg)
    {
        m_handler.obtainMessage(MESSAGE_TOAST, msg.getBytes().length, -1, msg.getBytes()).sendToTarget();
    }

    // Initialize the bluetooth hardware adapter
    private synchronized void setupBluetooth()
    {
        l("Getting adapter");
        m_bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();


        if (m_bluetoothAdapter == null) {
            l("Bluetooth not detected");
            msgToast("Error, Bluetooth not Detected!");
            bluetoothFailed = true;
        }

        if (!bluetoothFailed && !m_bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }
        else
            setupThreads();
    }

    //
    // This is to handle the enable bluetooth activity,
    // and disable all attempts at bluetooth functionality
    // if for some reason the user denies permission
    //
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {

        if (requestCode==1)
        {
            if (resultCode==RESULT_OK)
            {
                bluetoothFailed = false;
            }
            else
                bluetoothFailed = true;
            setupThreads();
        }
    }

    // Accept incoming bluetooth connections thread, actual member and the definition
    AcceptThread acceptThread;
    private class AcceptThread extends Thread {
        public final BluetoothServerSocket connectionSocket;
        public AcceptThread()
        {
            BluetoothServerSocket tmp = null;
            try
            {
                tmp = m_bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("ConnectDevice", uuid);
            }
            catch (java.io.IOException e)
            {
                Log.e("[Bluetooth]", "Socket connection failed", e);
            }


            connectionSocket = tmp;
        }

        public void run()
        {
            BluetoothSocket s = null;

            while (!Thread.currentThread().isInterrupted())
            {
                BluetoothSocket conn = null;
                try
                {
                    conn = connectionSocket.accept();
                }
                catch (java.io.IOException e)
                {
                    // Log.e("[Bluetooth]", "Socket acception failed", e);
                }

                if (conn != null)
                {
                    connectSocket(conn);
                    MSG(MESSAGE_CONNECTED);
                }
            }
            l("Accept Thread Ended!");
        }

        public void cancel()
        {
            try
            {
                connectionSocket.close();
            }
            catch (java.io.IOException e)
            {
                // Log.e("[Bluetooth]", "Socket close failed", e);
            }
        }
    }

    private void connectSocket(BluetoothSocket connection)
    {
        if (connectedThreads.size() < allowedDevices)
        {
            l("Received a connection, adding a new thread: " + connectedThreads.size() + 1);
            ConnectedThread thread = new ConnectedThread(connection);
            thread.setId(connectedThreads.size() + 1);
            thread.start();
            connectedThreads.add(thread);
        }
    }

    //
    // An arraylist of threads for each connected device,
    // with a unique id for when they finish so they may be removed
    //
    ArrayList<ConnectedThread> connectedThreads = new ArrayList<>();
    private class ConnectedThread extends Thread
    {
        private BluetoothSocket connectedSocket;
        private byte[] buffer;
        private int id;

        private void setId(int i ) { id = i; }

        public ConnectedThread(BluetoothSocket sockets)
        {
            connectedSocket = sockets;
        }

        public void close()
        {
            try
            {
                connectedSocket.close();
            }
            catch (Exception e)
            {
                l("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
        public void run()
        {
            while (!Thread.currentThread().isInterrupted())
            {
                InputStream stream;
                InputStream tmpIn = null;
                try {
                    l("Loading input stream");
                    tmpIn = connectedSocket.getInputStream();
                } catch (IOException e) {
                    Log.e("[Bluetooth]", "Error occurred when creating input stream", e);
                }
                stream = tmpIn;

                l("Reading stream");
                if (!read(stream))
                {
                    break;
                }

                if (Thread.currentThread().isInterrupted())
                {
                    break;
                }
            }
            l("Connected Thread Ended!!!");
            disconnect(id);
        }

        private boolean read(InputStream stream)
        {
            buffer = new byte[1024];
            int numBytes;
            try
            {
                numBytes = stream.read(buffer);

                l("Reading Bytes of Length:" + numBytes);

                m_handler.obtainMessage(MESSAGE_INPUT, numBytes, -1, new String(buffer, "UTF-8").substring(0, numBytes).replace("\0", "")).sendToTarget();
                return true;
            }
            catch (java.io.IOException e)
            {
                Log.d("[Bluetooth]", "Input stream disconnected", e);
                MSG(MESSAGE_DISCONNECTED);
                return false;
            }
        }


        public void write(byte[] bytes)
        {
            l("Writing: " + new String(bytes));
            l("Bytes Length: " + bytes.length);
            OutputStream stream;

            OutputStream tmpOut = null;
            try {
                tmpOut = connectedSocket.getOutputStream();
            } catch (IOException e) {
                Log.e("[Bluetooth]", "Error occurred when creating output stream", e);
            }
            stream = tmpOut;

            try
            {
                l("Writing bytes to outstream");
                stream.write(bytes);
            }
            catch (Exception e)
            {
                Log.e("[Bluetooth]", "Failed to send data", e);
                disconnect(id);
            }
        }

        /*
        public void write(byte[] bytes, int device)
        {
            l("Writing " + new String(bytes));
            l("Bytes length: " + bytes.length);
            OutputStream out = null;
            try
            {
                out = connectedSockets.get(device).getOutputStream();
                out.write(bytes);
            }
            catch (IndexOutOfBoundsException e)
            {
                l("Failed to write, device not found at index: " + device);
            }
            catch (IOException e)
            {
                l("Failed to write. IOException." + e.getMessage());
                e.printStackTrace();
            }
        }
        */

        public void cancel()
        {
            try
            {
                connectedSocket.close();
            }
            catch (java.io.IOException e)
            {
                Log.e("[Bluetooth]", "Failed to close socket", e);
            }
        }
    }

    // Disconnect a specific connected device, usually called from the thread itself
    private synchronized void disconnect(int id)
    {
        if (id < allowedDevices)
        {
            connectedThreads.get(id - 1).close();
            connectedThreads.get(id - 1).interrupt();
            connectedThreads.remove(id - 1);
        }
    }

    // When the activity is finished, clean up all of the bluetooth elements
    @Override
    public void onDestroy()
    {
        super.onDestroy();
        l("Destroying application threads");
        if (bluetoothFailed)
            return;
        if (acceptThread.connectionSocket != null && ! acceptThread.isInterrupted())
        {
            try
            {
                acceptThread.connectionSocket.close();
            } catch (java.io.IOException e)
            {
                l("Connection socket closing failed: " + e.getMessage());
            }
        }
        for (ConnectedThread thread : connectedThreads)
        {
            thread.close();
            thread.interrupt();
        }

    }
}