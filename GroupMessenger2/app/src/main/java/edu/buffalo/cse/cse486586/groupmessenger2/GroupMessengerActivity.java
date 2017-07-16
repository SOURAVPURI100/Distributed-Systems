package edu.buffalo.cse.cse486586.groupmessenger2;

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
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import static android.R.id.list;
import static android.content.ContentValues.TAG;
//import static edu.buffalo.cse.cse486586.groupmessenger2.GroupMessengerActivity.REMOTE_PORT;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
class Message_class{
    String message;
    String ident;
    int proposed;
    boolean agreed;
    long time;

    public Message_class(String message, String ident, int proposed, long time){
        this.message = message;
        this.ident = ident;
        this.proposed = proposed;
        this.agreed = false;
        this.time = time;
    }

}
// Declaration of comparator class
class Comp implements Comparator<Message_class> {

    public int compare(Message_class obj1, Message_class obj2) {
        if(obj1.proposed != obj2.proposed)
            return obj1.proposed - obj2.proposed;
//
//        int ident1 = Integer.parseInt(obj1.ident.substring(0,5));
//        int ident2 = Integer.parseInt(obj2.ident.substring(0,5));
        return obj1.ident.compareTo(obj2.ident);
//        return ident1 - ident2;
    }
}

//class Comp_Agreed implements Comparator<Message_class> {
//
//    public int compare(Message_class obj1, Message_class obj2) {
//
//        if(obj1.agreed != obj2.agreed)
//            return (obj1.agreed - obj2.agreed);
//
//        int ident1 = Integer.parseInt(obj1.ident.substring(0,5));
//        int ident2 = Integer.parseInt(obj2.ident.substring(0,5));
//        return ident1 - ident2;
//    }
//}
public class GroupMessengerActivity extends Activity {
    static final String[] REMOTE_PORT = {"11108", "11112", "11116", "11120", "11124"};
    static final int SERVER_PORT = 10000;
    static int msg_id = 0;
    static Set<String> DEL_REMOTE_PORT =new HashSet<String>();

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

                String msg = editText.getText().toString() + "\n";
                editText.setText(""); // This is one way to reset the input box.
                TextView localTextView = (TextView) findViewById(R.id.textView1);
                String[] msg_display = msg.split("\n");
                for (int i = 0; i < msg_display.length; i++)
                    localTextView.append("\t" + msg_display[i] + "\n"); // This is one way to display a string.
                // craete a client asynchronous task
                System.out.print("abc "+msg);
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
            int sequence = -1;
            int Proposed = 0, Agreed =0;
            Comp comp_obj = new Comp();
            PriorityQueue<Message_class> queue = new PriorityQueue<Message_class>(50, comp_obj);

                while (true) {
                   // sequence++;
                    //String message = "";
                    String msg="";
                    //System.out.println("ababab");

                    try {
                    Socket socket = serverSocket.accept();
                        socket.setSoTimeout(500);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    while (true) {
                        String msg_line = in.readLine();
                        if (msg_line == null || msg_line.isEmpty())
                            break;
                        msg = msg+msg_line+"\n";
                    }
                        System.out.println("msg received " + msg);

                        if(msg.charAt(0) == 'm'){
                            // If message recieved is a new message
                            String[] msgs = msg.split("-", 2);
                            String id = msgs[0].substring(1);
                            String msg_recvd = msgs[1];
                            Proposed = Math.max(Proposed, Agreed) +1;
                            long time = System.currentTimeMillis();
                            Message_class msg_obj = new Message_class(msg_recvd, id, Proposed, time);
                            System.out.print("time is "+time);
                            queue.add(msg_obj);
                            OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());
                            BufferedWriter bw = new BufferedWriter(out);
                            System.out.println("Proposed "+Proposed);
                            bw.write(Proposed+"");
                            bw.flush();
                            bw.close();
                        }
                        else{
//                            If message recieved is a new agreed ID

                            String[] msgs = msg.split("-", 2);
                            String id = msgs[0].substring(1);
                            String[] number = msgs[1].split("\n");
                            System.out.println("msgs[1]"+number[0]+"000");
                            int agreed_value = Integer.parseInt(number[0]);
                            // Add this agreed value to the concerned id in Priority Queue
                            System.out.println("Iterator");
                            Iterator<Message_class> itr = queue.iterator();

                            while(itr.hasNext()){
                                Message_class msg_obj = itr.next();
                                if(msg_obj.ident.equals(id)) {
                                    queue.remove(msg_obj);
                                    msg_obj.proposed = agreed_value;
                                    msg_obj.agreed = true;
                                    queue.add(msg_obj);
                                    break;
                                }

                            }
                            System.out.println("agreed_value "+agreed_value);
                            // Update value of global Agreed value
                            Agreed = Math.max(Agreed, agreed_value);

                            // Check if top element in queue has not been given agreed number since long
                            // then delete those proposed only messages
                            while(!queue.isEmpty() && (System.currentTimeMillis() - queue.peek().time) > 2000
                                    && queue.peek().agreed == false){
                                System.out.println("Messege deleted from queue curr time "+System.currentTimeMillis()
                                        +" saved time was "+queue.peek().time);
                                queue.poll();
                            }

                            // added code need to be deleted

                            while(!queue.isEmpty() && queue.peek().agreed == false){
                                Message_class msg_obj_temp = queue.peek();
                                String temp_port = msg_obj_temp.ident.substring(0,5);
                                System.out.println("port number to connect is "+temp_port);
                                try{

                                    Socket socket_temp = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                            Integer.parseInt(temp_port));
                                    socket_temp.close();
                                    break;

                                }
                                catch (SocketTimeoutException e){
                                    queue.poll();
                                    System.out.println("Server check TimeoutException");
                                }catch (SocketException e) {
                                   queue.poll();
                                    System.out.println("Server check  Exception");
                                }


                            }

                            // added code need to be deleted

                            // Check if the top element in Queue is agreed or not
                            while(!queue.isEmpty() && queue.peek().agreed == true){
                                String send_msg = queue.poll().message;
                                publishProgress(send_msg);
                                // Call Build method by passing content resolver
                                sequence++;
                                build(getContentResolver(), send_msg, sequence);
                            }
//************************** commented code
                            System.out.println("Iterator");
                            Iterator<Message_class> itr1 = queue.iterator();

                            while(itr1.hasNext()){
                                Message_class msg_obj = itr1.next();

                                System.out.println("message in queue is "+msg_obj.message);

                            }
                            System.out.println("Iterator");
 //*************************** Commented code*/
                            System.out.println("close stream ");
                            OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());
                            BufferedWriter bw = new BufferedWriter(out);
                            bw.write("OK");
                            bw.flush();
                            bw.close();

                        }
                }


                    catch (SocketTimeoutException e){

                        System.out.println("ServerTask Socket TimeoutException");
                    }
                    catch (SocketException e) {

                        System.out.println("ServerTask Socket Exception");
                    }
                    catch (EOFException e) {

                        System.out.println("ServerTask Socket EOFException");
                    }
                    catch (IOException e) {
                        Log.e(TAG, "ServerTask IOException");
                    }
                    catch (Exception e) {
                        Log.e(TAG, "ServerTask socket Exception");
                        e.printStackTrace();
                    }

                }

            //return null;
        }

        public void build(ContentResolver cr, String message, int sequence) {
            String seq = "" + sequence;
            mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
            ContentValues cv = new ContentValues();
            cv.put(KEY_FIELD, seq);
            cv.put(VALUE_FIELD, message);
            cr.insert(mUri, cv);
            System.out.println("message sequence " + seq + " value " + message);
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

                System.out.println("client value");
                int proposed_seq = Integer.MIN_VALUE;
                String msg = "";

                for (int j = 0; j < msgs.length - 1; j++)
                    msg = msg + msgs[j] + "\n";

                String myPort_Number = msgs[msgs.length -1];
                for (int i = 0; i < REMOTE_PORT.length; i++) {
                    // Check if remote port has failed or not
                        if (DEL_REMOTE_PORT.contains(REMOTE_PORT[i]))
                                continue;

                    try {
                        // Socket socket = new Socket();
                        // add timeout exception
//                        socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
//                                Integer.parseInt(REMOTE_PORT[i])), 1000);
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(REMOTE_PORT[i]));

                        // Add identifier to message before sending
                        String MsgToSend = 'm' + myPort_Number + msg_id + '-' + msg;
                        OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());
                        BufferedWriter bw = new BufferedWriter(out);
                        bw.write(MsgToSend);
                        bw.flush();
                        System.out.println("message sent " + MsgToSend);
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        String msgin = in.readLine();

                        while (true) {
                            if (!msgin.isEmpty()) {
                                System.out.println("aaaa" + msgin);
                                proposed_seq = Math.max(Integer.parseInt(msgin), proposed_seq);

                                break;
                            }

                        }
                        in.close();
                        bw.close();


                /*
                 * TODO: Fill in your client code that sends out a message.
                 */
                        socket.close();
                    }
                    catch (SocketTimeoutException e){
                        DEL_REMOTE_PORT.add(REMOTE_PORT[i]);
                        System.out.println("ClientTask Socket TimeoutException");
                    }catch (SocketException e) {
                        Log.e(TAG, "ClientTask Socket Exception"+e.getMessage());
                        DEL_REMOTE_PORT.add(REMOTE_PORT[i]);
                        System.out.println("ClientTask Socket Exception");
                    }
                    catch (EOFException e) {
                        DEL_REMOTE_PORT.add(REMOTE_PORT[i]);
                        System.out.println("ClientTask Socket EOFException");
                    }catch (IOException e) {
                        Log.e(TAG, "ClientTask socket IOException");
                        DEL_REMOTE_PORT.add(REMOTE_PORT[i]);
                        System.out.println("ClientTask Socket IOException");
                    }catch (Exception e) {
                        Log.e(TAG, "ClientTask socket Exception");
                         DEL_REMOTE_PORT.add(REMOTE_PORT[i]);
                        System.out.println("ClientTask Socket Exception");
                        e.printStackTrace();
                    }

                }


                for (int i = 0; i < REMOTE_PORT.length; i++) {

                    // Check if remote port has failed or not
                    if (DEL_REMOTE_PORT.contains(REMOTE_PORT[i]))
                        continue;
                    try {
                      //  Socket socket = new Socket();
                        // add timeout exception
//                        socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
//                                Integer.parseInt(REMOTE_PORT[i])), 1000);

                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(REMOTE_PORT[i]));

                    // Add identifier to message before sending
                    String agreed_msg = 'i'+myPort_Number+msg_id+'-'+proposed_seq + "\n";
                    OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());
                    BufferedWriter bw = new BufferedWriter(out);
                    bw.write(agreed_msg+"\n");
                    bw.flush();
                    System.out.println("agreed message sent " + agreed_msg);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String msgin = in.readLine();
                    while (true) {
                        if (msgin.equals("OK"))
                        {
                            break;
                        }

                    }
                    in.close();
                    bw.close();

                /*
                 * TODO: Fill in your client code that sends out a message.
                 */
                    socket.close();

                }
                catch (SocketTimeoutException e){
                    System.out.println("ClientTask Socket TimeoutException");
                    DEL_REMOTE_PORT.add(REMOTE_PORT[i]);
                }catch (SocketException e) {
                    Log.e(TAG, "ClientTask Socket Exception"+e.getMessage());
                        DEL_REMOTE_PORT.add(REMOTE_PORT[i]);
                    System.out.println("ClientTask Socket Exception");
                } catch (EOFException e) {
                        DEL_REMOTE_PORT.add(REMOTE_PORT[i]);
                        System.out.println("ClientTask Socket EOFException");
                    }
                        catch (IOException e) {
                    Log.e(TAG, "ClientTask socket IOException");
                        DEL_REMOTE_PORT.add(REMOTE_PORT[i]);
                    System.out.println("ClientTask Socket IOException");
                }
                    catch (Exception e) {
                        Log.e(TAG, "ClientTask socket Exception");
                        DEL_REMOTE_PORT.add(REMOTE_PORT[i]);
                        e.printStackTrace();
                        System.out.println("ClientTask Socket IOException");
                    }
                }
                msg_id++;
            return null;
        }
    }
}






