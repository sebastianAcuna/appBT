package com.example.smapdev.bluethoothapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import static android.bluetooth.BluetoothAdapter.STATE_CONNECTED;
import static android.bluetooth.BluetoothAdapter.STATE_CONNECTING;

public class MainActivity extends AppCompatActivity {

    TextView status,smg_box; /* interfaz*/
    Button button1,listen_buton,send_btn;
    ListView listView;
    EditText writeMsg;


    BluetoothAdapter bluetoothAdapter;  /* adaptador de bluetooth */
    BluetoothDevice[] btArray;  /* bt emparejados */


    /*
    * ESTADOS DE LA CONEXION BLUETOOTH
    * */



    SendReceive sendReceive;

    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    static final int STATE_CONNECTION_FAILED = 4;
    static final int STATE_MESSAGE_RECEIVED = 5;

    int REGUEST_ENABLE_BLUETOOTH=1;

    /*
        =======================================================
    */

    private static final String APP_NAME = "BTchat";
    private static final UUID MY_UUID=UUID.fromString("8ce255c0-223a-11e0-ac64-0803450c9a66");



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        status = (TextView) findViewById(R.id.status);
        smg_box = (TextView) findViewById(R.id.smg_box);
        button1 = (Button) findViewById(R.id.button1);
        send_btn = (Button) findViewById(R.id.send_btn);
        listen_buton = (Button) findViewById(R.id.listen_buton);
        listView = (ListView) findViewById(R.id.listview);
        writeMsg = (EditText) findViewById(R.id.writeMsg);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter != null){
            if (!bluetoothAdapter.isEnabled()){
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, REGUEST_ENABLE_BLUETOOTH);
                implementListeners();
            }

        }




    }


    private void implementListeners(){

        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Set<BluetoothDevice> bt = bluetoothAdapter.getBondedDevices();
                String[] strings = new String[bt.size()];
                btArray=new BluetoothDevice[bt.size()];
                int index = 0;

                if (bt.size() > 0){
                    for (BluetoothDevice device : bt){
                        btArray[index] = device;
                        strings[index] = device.getName();
                        index++;
                    }

                    ArrayAdapter arrayAdapter = new ArrayAdapter<>(getApplicationContext(),android.R.layout.simple_list_item_1, strings);
                    listView.setAdapter(arrayAdapter);
                }
            }
        });


        listen_buton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ServerClass serverClass = new ServerClass();
                serverClass.start();
            }
        });


        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ClientClass clientClass = new ClientClass(btArray[position]);
                clientClass.start();

                status.setText("Connecting");
            }
        });


        send_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String string = String.valueOf(writeMsg.getText());

                sendReceive.write(string.getBytes());
            }
        });



    }

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case STATE_LISTENING:
                    status.setText("Listening");
                    break;
                case STATE_CONNECTING:
                    status.setText("Conecting");
                    break;

                case STATE_CONNECTED:
                    status.setText("Connected");
                    break;
                case STATE_CONNECTION_FAILED:
                    status.setText("Connection failed");
                    break;
                case STATE_MESSAGE_RECEIVED:
//                    we will write it later
                    byte[] readBuff = (byte[]) msg.obj;
                    String tempMsg = new String(readBuff,0,msg.arg1);
                    smg_box.setText(tempMsg);
                    break;
            }

            return true;
        }
    });



    private class ServerClass extends Thread{

        BluetoothServerSocket serverSocket;
        public ServerClass(){
            try {
                serverSocket=bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME,MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run(){

            BluetoothSocket socket = null;

            while(socket == null){
                try {
                    Message message = Message.obtain();
                    message.what=STATE_CONNECTING;
                    handler.sendMessage(message);

                    socket = serverSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();

                    Message message = Message.obtain();
                    message.what=STATE_CONNECTION_FAILED;

                    handler.sendMessage(message);
                }



                if (socket != null){

                    Message message = Message.obtain();
                    message.what=STATE_CONNECTED;
                    handler.sendMessage(message);

                    sendReceive = new SendReceive(socket);
                    sendReceive.start();
//                    write some code for send / receive

                    break;
                }
            }

        }

    }




    private class ClientClass extends  Thread{

        private BluetoothDevice device;
        private BluetoothSocket socket;

        public ClientClass(BluetoothDevice device1){

            device = device1;

            try {
                socket = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run(){
            try {
                socket.connect();
                Message message = Message.obtain();
                message.what= STATE_CONNECTED;
                handler.sendMessage(message);

                sendReceive = new SendReceive(socket);
                sendReceive.start();

            } catch (IOException e) {
                e.printStackTrace();

                Message message = Message.obtain();
                message.what= STATE_CONNECTION_FAILED;
                handler.sendMessage(message);
            }
        }

    }



    private class SendReceive extends Thread{
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public SendReceive (BluetoothSocket socket){
            bluetoothSocket = socket;
            InputStream tempIn = null;
            OutputStream tempOut = null;

            try {
                tempIn=bluetoothSocket.getInputStream();
                tempOut=bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }


            inputStream=tempIn;
            outputStream=tempOut;
        }

        public void run(){
            byte[] buffer = new byte[1024];
            int bytes;

            while(true){
                try {
                    bytes=inputStream.read(buffer);
                    handler.obtainMessage(STATE_MESSAGE_RECEIVED,bytes,-1,buffer).sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void write(byte[] bytes){

            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
