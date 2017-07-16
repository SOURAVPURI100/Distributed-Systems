package edu.buffalo.cse.cse486586.groupmessenger1;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import static android.content.ContentValues.TAG;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    static final String[] REMOTE_PORT = {"11108", "11112", "11116", "11120", "11124"};
    static final int SERVER_PORT = 10000;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        
        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));


        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        //////////////Begin of Code

        // Create myport value for client
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {

            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

//        Register and define the Send button event
        final Button button = (Button) findViewById(R.id.button4);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                final EditText editText = (EditText) findViewById(R.id.editText1);

                String msg = editText.getText().toString() +"\n";
                editText.setText(""); // This is one way to reset the input box.
                TextView localTextView = (TextView) findViewById(R.id.textView1);
                String[] msg_display = msg.split("\n");
                for(int i=0; i<msg_display.length;i++)
                    localTextView.append("\t" + msg_display[i]+"\n"); // This is one way to display a string.
                // craete a client asynchronous task
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
            }
        });


        ///////////// End of code
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    // Server class
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        private static final String KEY_FIELD = "key";
        private static final String VALUE_FIELD = "value";
        private Uri mUri;

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            int sequence =-1;
            try {
                while(true) {
                    sequence++;
                    String message ="";
                    Socket socket = serverSocket.accept();
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    while(true) {
                        String msg = in.readLine();
                        if(msg.isEmpty())
                            break;
                        System.out.println("msg received "+msg);
                        message = message + msg +"\n";
                        publishProgress(msg);
                    }
                    // Call Build method by passing content resolver
                    build(getContentResolver(), message, sequence);

                    OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());
                    BufferedWriter bw = new BufferedWriter(out);
                    bw.write("OK");
                    bw.flush();
                    bw.close();

                }

            }
            catch(IOException e)
            {
                Log.e(TAG, "Error in receiveing client request on server");
            }


            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            return null;
        }

        public void build(ContentResolver cr, String message, int sequence)
        {
            String seq = ""+sequence;
            mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger1.provider");
            ContentValues cv = new ContentValues();
            cv.put(KEY_FIELD, seq);
            cv.put(VALUE_FIELD, message);
            cr.insert(mUri,cv);
            System.out.println("message sequence "+seq+" value "+message);
        }

        private Uri buildUri(String scheme, String authority) {
            Uri.Builder uriBuilder = new Uri.Builder();
            uriBuilder.authority(authority);
            uriBuilder.scheme(scheme);
            return uriBuilder.build();
        }

        protected void onProgressUpdate(String... strings) {
            /*
             * The following code displays what is received in doInBackground().
             */

            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\t\n");
            return;
        }
    }


    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {
                String msg ="";
                for(int j=0; j<msgs.length -1; j++)
                    msg = msg + msgs[j]+"\n";
                for(int i =0; i<REMOTE_PORT.length; i++) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(REMOTE_PORT[i]));

                    OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());
                    BufferedWriter bw = new BufferedWriter(out);
                    bw.write(msg);
                    bw.flush();
                    System.out.println("message sent " +msg);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String msgin = in.readLine();
                    while(true)
                    {
                        if (msgin.equals("OK"))
                            break;
                    }
                    in.close();
                    bw.close();
                /*
                 * TODO: Fill in your client code that sends out a message.
                 */
                    socket.close();
                }
            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            }

            return null;
        }
    }
}

