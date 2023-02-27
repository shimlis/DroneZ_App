package com.example.dronez_beta;

import static android.os.SystemClock.sleep;
import static java.lang.Thread.interrupted;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.controlwear.virtual.joystick.android.JoystickView;

public class MainActivity extends AppCompatActivity {

    private static final int[] RC = {0, 0, 0};  // Integer array to store the strength from the joystick
    Pattern statePattern = Pattern.compile("-*\\d{0,3}\\.?\\d{0,2}[^\\D\\W\\s]");  // a regex pattern to read the tello state

    TextView droneBattery;
    TextView wifiConnection;
    private Handler telloStateHandler;  // and handler needs to be created to display the tello state values in the UI in realtime

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Sets the  background color white
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(Color.parseColor("#000000"));
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(Color.parseColor("#000000"));
        }

        // Removes the action bar
        getSupportActionBar().hide();

        droneBattery = findViewById(R.id.droneBattery);
        wifiConnection = findViewById(R.id.wifiConnection);

        // Takes the strength value of the left joystick depending on the angle
        JoystickView leftjoystick = (JoystickView) findViewById(R.id.joystickViewLeft);
        leftjoystick.setOnMoveListener((angle, strength) -> {

            if (angle >45 && angle <=135){
                RC[2]= strength;
            }
            if (angle >226 && angle <=315){
                strength *= -1;
                RC[2]= strength;
            }
            if (angle >135 && angle <=225){
                strength *= -1;
                RC[3]= strength;
            }
            if (angle >316 && angle <=359 || angle >0 && angle <=45){
                RC[3]= strength;
            }

            // Send the strength data to drone using command
            telloConnect("rc "+ RC[0] +" "+ RC[1] +" "+ RC[2] +" "+ RC[3]); // send the command eg,. 'rc 10 00 32 00'
            Arrays.fill(RC, 0); // reset the array with 0 after every virtual joystick move
        });
    }

        // Connects with tello drone
        public void telloConnect ( final String strCommand){
            new Thread(new Runnable() { // create a new runnable thread to handle tello state
                public void run() {
                    Boolean run = true; // always keep running once initiated
                    try {
                        if (strCommand == "disconnect") {
                            run = false;
                        }
                        DatagramSocket udpSocket = new DatagramSocket(null); // create a datagram socket with null attribute so that a dynamic port address can be chosen later on

                        InetAddress serverAddr = InetAddress.getByName("192.168.10.1");     // set the tello IP address (refer Tello SDK 1.3)
                        byte[] buf = (strCommand).getBytes("UTF-8");             // command needs to be in UTF-8
                        DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddr, 8889); // crate new datagram packet
                        udpSocket.send(packet);     // send packets to port 8889
                        while (run) {
                            byte[] message = new byte[1518];        // create a new byte message (you can change the size)
                            DatagramPacket rpacket = new DatagramPacket(message, message.length);
                            Log.i("UDP client: ", "about to wait to receive");
                            udpSocket.setSoTimeout(2000);           // set a timeout to close the connection
                            udpSocket.receive(rpacket);             // receive the response packet from tello
                            String text = new String(message, 0, rpacket.getLength()); // convert the message to text
                            Log.d("Received text", text);       // display the text as log in Logcat
                            new Thread(new Runnable() {             // create a new thread to stream tello state
                                @Override
                                public void run() {
                                    while (!interrupted()) {
                                        sleep(2000);            // I chose 2 seconds as the delay
                                        byte[] buf = new byte[0];
                                        try {
                                            buf = ("battery?").getBytes("UTF-8");
                                            DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddr, 8889);
                                            udpSocket.send(packet);

                                            DatagramSocket socket = new DatagramSocket(null);   // create a new datagram socket
                                            socket.setReuseAddress(true);                               // set the reuse
                                            socket.setBroadcast(true);
                                            socket.bind(new InetSocketAddress(8890));              // bind to tello state port (refer to SDK 1.3)

                                            byte[] message = new byte[1518];
                                            DatagramPacket rpacket = new DatagramPacket(message, message.length); //, serverAddr, 8890
                                            socket.receive(rpacket);
                                            String text = new String(message, 0, rpacket.getLength());
                                            Matcher DCML = statePattern.matcher(text);                  // use the regex pattern initiated at the beginning of the code to parse the response from tell drone
                                            List<String> dec = new ArrayList<String>();                      // parse the response and store it in an array
                                            while (DCML.find()) {
                                                dec.add(DCML.group());
                                            }

                                            Log.d("Battery Charge : ", text + "%");
                                            telloStateHandler.post(new Runnable() {                     // use the initiated handler to post the tello state output the drone controller UI
                                                @Override
                                                public void run() {
                                                    try {
                                                        droneBattery.setText("Battery: " + dec.get(10) + "%");
//                                                        if (Integer.parseInt(dec.get(10)) <= 15){
//                                                            jdroneBattery.setBackgroundResource(R.drawable.rounded_corner_red); // if battery percentage is below 15 set the background of text to red
//                                                        }
//                                                        else {
//                                                            jdroneBattery.setBackgroundResource(R.drawable.rounded_corner_green); // else display batter percentage with green background
//                                                        }
                                                        if (Integer.parseInt(dec.get(10)) != 0) {
                                                            wifiConnection.setBackgroundResource(R.drawable.connect_drone);     // if wifi is connected and is active then display with green background
                                                            wifiConnection.setText("Connection: connected");
                                                        }
//                                                        jdroneTOF.setText("TOF: "+dec.get(8)+"cm");
//                                                        jdroneBaro.setText("Baro: "+dec.get(11)+"m");
//                                                        jdroneHeight.setText("Height: "+dec.get(9));
//                                                        jdroneTemperature.setText("Temperature: "+dec.get(7)+"C");
//                                                        jdroneSpeed.setText("Speed :"+ Integer.parseInt(dec.get(3)) + Integer.parseInt(dec.get(4)) + Integer.parseInt(dec.get(5))+"cm/s");
//                                                        jdroneAccleration.setText("Acceleration: "+Math.round(Math.sqrt(Math.pow(Double.parseDouble(dec.get(13)),2)+Math.pow(Double.parseDouble(dec.get(14)),2)+Math.pow(Double.parseDouble(dec.get(15)),2)))+"g");
                                                        // https://physics.stackexchange.com/questions/41653/how-do-i-get-the-total-acceleration-from-3-axes
                                                        // for calculating acceleration I referred to the above link

                                                        telloStateHandler.removeCallbacks(this);

                                                    } catch (Exception e) {
                                                        Log.e("Array out of bounds", "error", e);
                                                    }
                                                }
                                            });

                                        } catch (UnsupportedEncodingException e) {
                                            e.printStackTrace();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }

                                    }
                                }
                            }).start();

                        }

                    } catch (SocketException | UnknownHostException e) {
                        Log.e("Socket Open:", "Error:", e);
                    } catch (IOException e) {
                        Log.e("IOException", "error", e);
                    }

                }
            }).start();
        }
    }
