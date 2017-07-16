package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.Log;

import static android.content.ContentValues.TAG;
import static java.lang.Thread.sleep;

public class SimpleDhtProvider extends ContentProvider {
    static final int SERVER_PORT = 10000;
    static final String[] REMOTE_PORT = {"11108", "11112", "11116", "11120", "11124"};
    static String successor = "";   // Hashed value
    static String predecessor = ""; // Hashed value
    static String successor_id = ""; //  Node ID for connection e.g. 11108
    static String predecessor_id = ""; //Node ID for connection e.g. 11108
    static String myPort = "";
    static String My_port_value = "";
    static final String node_join = "Node_Join";
    static final String succ_info = "succ_info";
    static final String msg_info = "msg_info";
    static final String succ_only = "succ_only";
    static final String pred_only = "pred_only";
    static final String map_data = "map_data";
    static final String delete_data = "delete_data";
    static final String delete_key = "delete_key";
    static final String send_data = "send_data";
    static final String find_key = "find_key";
    static final String found_val = "found_val";
    static final String split = "@#$";
    static final String split_new = "&%12";
    static String found_value ="";
    static boolean global_flag = false;
    static int count = 1;
    static final String split_break = "[@][#][$]";
    static final String split_new_break = "[&][%][1][2]";
    static Map<String, String> file_map = new HashMap<String, String>();
    static Map<String, String> all_map = new HashMap<String, String>();

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // Delete data based on value in selection
        Log.e(TAG, "Selection Parameter is "+selection);
        // Check if query is for global data or local data

        if(selection.contains("*")){

            if(!successor_id.isEmpty()) {
                file_map.clear();
                String msg = delete_data + split + myPort;
                new ClientRequest().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, successor_id);

            }
            else{
                file_map.clear();
            }


        }
        else if(selection.contains("@")){
            file_map.clear();
        }
        else{
            if(file_map.containsKey(selection))
                file_map.remove(selection);
            else{
                String msg = delete_key + split + selection;
                new ClientRequest().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, successor_id);

            }
        }


        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {

        String KEY_FIELD = "key";
        String filename = "";
        String value = "";
        for (String str : values.keySet()) {
            if (str.equals(KEY_FIELD)) {
                filename = values.getAsString(str);

            }
            else
                value = values.getAsString(str);

        }

        add_message(filename, value);

//        FileOutputStream outputStream;
//        try {
//            outputStream = getContext().openFileOutput(filename, Context.MODE_PRIVATE);
//            outputStream.write(value.getBytes());
//            outputStream.close();
//        } catch (Exception e) {
//            Log.e(TAG, "File write failed for " + values.toString());
//        }
//        Log.v("insert", values.toString());
        return uri;

    }

    // Begin of code

    public void add_message(String key, String value){
        try{
            // Generate hash id for this node
            String msg_value = genHash(key);
            if(predecessor_id.isEmpty() && successor_id.isEmpty()){
                file_map.put(key,value);
                return;
            }
            // Check if this key, value belongs to this node
            if(My_port_value.compareTo(predecessor) <0){

                if(msg_value.compareTo(predecessor) > 0){
                    file_map.put(key,value);
                    Log.e(TAG, "Message stored key "+key+" value "+value);
                }
                else if(msg_value.compareTo(My_port_value) <= 0) {
                    file_map.put(key, value);
                    Log.e(TAG, "Message stored key "+key+" value "+value);
                }
                else {
                    String msg = key+split+value;
                    new ClientInsert().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, successor_id);
                    Log.e(TAG, "Message sent to succ id "+successor_id);
                }
            }
            else{
                if(msg_value.compareTo(predecessor) > 0 && msg_value.compareTo(My_port_value) <= 0) {
                    file_map.put(key, value);
                    Log.e(TAG, "Message stored key "+key+" value "+value);
                }
                else {
                    String msg = key+split+value;
                    new ClientInsert().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, successor_id);
                    Log.e(TAG, "Message sent to succ id "+successor_id);
                }

            }

        }
        catch (Exception e) {

            Log.e(TAG, "Message hash conversion error");

        }

    }

    // End of Code

    @Override
    public boolean onCreate() {

        // Create myport value for client
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        // If this port's id is not 11108 then create a client task to notify port 11108
        System.out.println("My is " + myPort.equals("11108"));
        Log.e(TAG, "My is " + myPort.equals("11108"));
        try {

            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);

            if(!myPort.equals("11108")){
                System.out.println("client  is " + myPort);
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "11108", myPort);
        }

        System.out.println("My port number is " + myPort);

            // Generate hash id for this node
            My_port_value = genHash(Integer.parseInt(myPort)/2+"");
            System.out.println("My port value is " + My_port_value+" "+Integer.parseInt(myPort)/2 +"");
        } catch (IOException e) {

            Log.e(TAG, "Can't create a ServerSocket");

        }
        catch (Exception e) {

            Log.e(TAG, "OnCreate Exception for myPort");

        }

        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        Log.e(TAG, "Selection Parameter is "+selection);
        // Check if query is for global data or local data

        if(selection.contains("*")){
            // Fetch all data from all Nodes

            if(!successor_id.isEmpty()) {
                all_map.clear();
                global_flag = false;
                String msg = send_data + split + myPort;
                new ClientRequest().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, successor_id);

                while (true) {
                    if (global_flag == true)
                        break;
                }

                for (String key : file_map.keySet()) {
                    all_map.put(key, file_map.get(key));
                }

                String[] columns = {"key", "value"};

                MatrixCursor cursor = new MatrixCursor(columns);

                for (String key : all_map.keySet()) {
                    String[] values = {key, all_map.get(key)};
                    cursor.addRow(values);
                }

                return cursor;
            }
            else{
                String[] columns = {"key", "value"};

                MatrixCursor cursor = new MatrixCursor(columns);

                for(String key: file_map.keySet()) {
                    String[] values = {key, file_map.get(key)};
                    cursor.addRow(values);
                }
                return cursor;

            }


        }
       // else if(selection.contains("@")){
        else if(selection.contains("@")){
            String[] columns = {"key", "value"};

            MatrixCursor cursor = new MatrixCursor(columns);

            for(String key: file_map.keySet()) {
                String[] values = {key, file_map.get(key)};
                cursor.addRow(values);
            }
            return cursor;

        }
        else{
            // single key for query
            if(file_map.containsKey(selection)){

                String[] columns = {"key", "value"};

                MatrixCursor cursor = new MatrixCursor(columns);
                String[] values = {selection, file_map.get(selection)};
                cursor.addRow(values);
                return cursor;
            }

            else{
                global_flag = false;
                String msg = find_key + split + selection + split + myPort;
                Log.e(TAG, "Find key request is "+msg);
                new ClientRequest().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, successor_id);

                while (true) {
                    if (global_flag == true)
                        break;
                }
                String[] columns = {"key", "value"};

                MatrixCursor cursor = new MatrixCursor(columns);
                String[] values = {selection, found_value};
                cursor.addRow(values);
                return cursor;


            }


        }

//        FileInputStream InputStream;
//        String str = "";
//        String value = "";
//        try {
//            InputStream = getContext().openFileInput(selection);
//            BufferedReader in = new BufferedReader(new InputStreamReader(InputStream));
//            while ((str = in.readLine()) != null) {
//                value = value + str;
//            }
//            String[] columns = {"key", "value"};
//            String[] values = {selection, value};
//            MatrixCursor cursor = new MatrixCursor(columns);
//            cursor.addRow(values);
//            InputStream.close();
//            return cursor;
//        } catch (Exception e) {
//            Log.e(TAG, "File read failed for " + selection);
//        }
//
//        Log.v("query", selection);
        //return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }


    // Server class
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        private static final String KEY_FIELD = "key";
        private static final String VALUE_FIELD = "value";
        private Uri mUri;

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            while (true) {
                Log.e(TAG, "Server Started " + myPort);
                try {
                    String MsgToSend ="";
                    Log.e(TAG, "Server Socket " + myPort);
                    Socket socket = serverSocket.accept();
                    String msg = "";
                    //socket.setSoTimeout(500);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    while (true) {
                        String msg_line = in.readLine();
                        Log.e(TAG, "msg line " + msg_line);
                        if (msg_line == null || msg_line.isEmpty())
                            break;
                        msg = msg + msg_line;
                    }
                    Log.e(TAG, "msg received " + msg);
                    System.out.println("msg received " + msg);
                    //socket.close();
                    // Check if message contains a new join request
                    if (msg.contains(node_join)) {
//
//                        // added sleep
//                        if(myPort.contains("11108")){
//
//                            if(count > 3)
//                                sleep(1000);
//                            count++;
//                        }


                        // added sleep
                        String[] messages = msg.split("-");
                        String node_id = messages[1];
                        // Generate hash id for this node

                        String new_port_value = genHash(Integer.parseInt(node_id)/2 +"");
                        Log.e(TAG, "new port value "+new_port_value+" "+Integer.parseInt(node_id)/2 +"");
                        // check if successor and predecessor is null, then this node becomes
                        // successor and predecessor of new node
                        if(successor.isEmpty() && predecessor.isEmpty()){
                            successor = new_port_value;
                            predecessor = new_port_value;
                            successor_id = node_id;
                            predecessor_id = node_id;

                            System.out.println("succ is "+successor+" pred is "+predecessor+" " +
                                    " succ id "+successor_id+" pred id "+predecessor_id);

                            Log.e(TAG, "succ is "+successor+" pred is "+predecessor+" " +
                                    " succ id "+successor_id+" pred id "+predecessor_id);
                            // Add identifier to message before sending
//                            Successor-Predecessor-Sucessor_id-Predecessor_id("11108")
                            MsgToSend = succ_info + '-' + My_port_value+"-"+My_port_value
                                               + "-" + myPort + "-" + myPort;
                            send_request(node_id,MsgToSend);

                        }

                        // Begin of code commented ...........................................
                        else if(My_port_value.compareTo(successor) > 0){
                            // if my port value is greater than my successor, it means my node
                            // is the highest node

                            if(new_port_value.compareTo(My_port_value)> 0){
                                // fit the node inside current node and successor
                                // Send info about successor and predecessor to new node
                                MsgToSend = succ_info + '-' + successor+"-"+My_port_value
                                            + "-" + successor_id + "-" + myPort;
                                send_request(node_id, MsgToSend);
                                    // Send info about predeccessor to current successor

                                MsgToSend = pred_only + '-' + new_port_value+"-"+node_id;
                                send_request(successor_id, MsgToSend);

//                              update current node's successor
                                successor = new_port_value;
                                successor_id = node_id;
                                Log.e(TAG, "succ is "+successor+" succ id "+successor_id);

                            }

                            else if(new_port_value.compareTo(My_port_value)< 0 && new_port_value.compareTo(successor) < 0){
                                     // in this case, if new node is lesser than my port and
//                                    // lesser than successor
//                                    // Send info about successor and predecessor to new node
                                    MsgToSend = succ_info + '-' + successor+"-"+My_port_value
                                            + "-" + successor_id + "-" + myPort;
                                    send_request(node_id, MsgToSend);

                                    // Send info about predecessor to current successor

                                    MsgToSend = pred_only + '-' + new_port_value+"-"+node_id;
                                    send_request(successor_id, MsgToSend);

//                                  update current node's successor
                                    successor = new_port_value;
                                    successor_id = node_id;

                                    Log.e(TAG, "succ is "+successor+" succ id "+successor_id);

                            }
                            else{
                                send_request(successor_id, msg);
                            }

                        }
                        else{
                            // if my node  is lesser than successor node
                            if(new_port_value.compareTo(My_port_value)> 0 && new_port_value.compareTo(successor) < 0){

                                // fit the node inside current node and successor
                                // Send info about successor and predecessor to new node
                                MsgToSend = succ_info + '-' + successor+"-"+My_port_value
                                        + "-" + successor_id + "-" + myPort;
                                send_request(node_id, MsgToSend);
                                // Send info about predeccessor to current successor

                                MsgToSend = pred_only + '-' + new_port_value+"-"+node_id;
                                send_request(successor_id, MsgToSend);

//                              update current node's successor
                                successor = new_port_value;
                                successor_id = node_id;
                                Log.e(TAG, "succ is "+successor+" succ id "+successor_id);


                            }

                            else{
                                send_request(successor_id, msg);
                            }


                        }

                        // End of code commented ...........................................


//                        // if my port  value is greater tha new port value
//                        else if(My_port_value.compareTo(new_port_value) > 0){
//                            // check if my predecessor is smaller than current node
//                            if(My_port_value.compareTo(predecessor) > 0){
//
//                                if(predecessor.compareTo(new_port_value) > 0){
//                                    // As new node has lower value than predecessor
//                                    // Send the request to predecesor to attach this node in this case
//                                    send_request(predecessor_id, msg);
//                                }
//                                else
//                                {
//                                    // in this case, if new node is lesser than my port but greater
//                                    // than predecessor
//                                    // Send info about successor and predecessor to new node
//                                    MsgToSend = succ_info + '-' + My_port_value+"-"+predecessor
//                                            + "-" + myPort + "-" + predecessor_id;
//                                    send_request(node_id, MsgToSend);
//
//                                    // Send info about successor to current predecessor
//
//                                    MsgToSend = succ_only + '-' + new_port_value+"-"+node_id;
//                                    send_request(predecessor_id, MsgToSend);
//
////                                  update current node's successor and predecessor
//                                    predecessor = new_port_value;
//                                    predecessor_id = node_id;
//
//                                    Log.e(TAG, "pred is "+predecessor+" pred id "+predecessor_id);
//
//                                }
//
//                            }
//                            else{
//
//                                // in this case, if new node is lesser than my port
//                                // Send info about successor and predecessor to new node
//                                MsgToSend = succ_info + '-' + My_port_value+"-"+predecessor
//                                        + "-" + myPort + "-" + predecessor_id;
//                                send_request(node_id, MsgToSend);
//
//                                // Send info about successor to current predecessor
//
//                                MsgToSend = succ_only + '-' + new_port_value+"-"+node_id;
//                                send_request(predecessor_id, MsgToSend);
//
////                                  update current node's successor and predecessor
//                                predecessor = new_port_value;
//                                predecessor_id = node_id;
//                                Log.e(TAG, "pred is "+predecessor+" pred id "+predecessor_id);
//
//                            }
//
//
//
//                        }
//                        else{
//                            // if my port  value is lesser than new port value
//                            // check if my successor is smaller than current node
//                            if(successor.compareTo(My_port_value) > 0){
//
//                                // if successor is greater than new node value
//                                if(successor.compareTo(new_port_value) > 0){
//
////                                   // fit the node inside current node and successor
//                                    // Send info about successor and predecessor to new node
//                                    MsgToSend = succ_info + '-' + successor+"-"+My_port_value
//                                            + "-" + successor_id + "-" + myPort;
//                                    send_request(node_id, MsgToSend);
//                                    // Send info about predeccessor to current successor
//
//                                    MsgToSend = pred_only + '-' + new_port_value+"-"+node_id;
//                                    send_request(successor_id, MsgToSend);
//
////                                  update current node's successor
//                                    successor = new_port_value;
//                                    successor_id = node_id;
//                                    Log.e(TAG, "succ is "+successor+" succ id "+successor_id);
//                                }
//                                else{
//
//                                    send_request(successor_id, msg);
//
//                                }
//
//
//                            }
//                            else{
//                               // fit the node inside current node and successor
//                                // Send info about successor and predecessor to new node
//                                MsgToSend = succ_info + '-' + successor+"-"+My_port_value
//                                        + "-" + successor_id + "-" + myPort;
//                                send_request(node_id, MsgToSend);
//                                // Send info about predeccessor to current successor
//
//                                MsgToSend = pred_only + '-' + new_port_value+"-"+node_id;
//                                send_request(successor_id, MsgToSend);
//
////                                  update current node's successor
//                                successor = new_port_value;
//                                successor_id = node_id;
//                                Log.e(TAG, "succ is "+successor+" succ id "+successor_id);
//
//                            }
//
//
//                        }

                    }
                    // to update succesor and predecesor info
                    else if (msg.contains(succ_info)) {
                        String[] values = msg.split("-");
                        // fill succ and pred info
                        successor = values[1];
                        predecessor = values[2];
                        successor_id = values[3];
                        predecessor_id = values[4];

                        System.out.println("info succ is "+successor+" pred is "+predecessor+" " +
                                " succ id "+successor_id+" pred id "+predecessor_id);
                        Log.e(TAG, "info succ is "+successor+" pred is "+predecessor+" " +
                                " succ id "+successor_id+" pred id "+predecessor_id);
                    }
                    // to update succesor info
                    else if (msg.contains(succ_only)) {
                        String[] values = msg.split("-");
                        // fill succ and pred info
                        successor = values[1];
                        successor_id = values[2];
                        System.out.println("succ only "+successor+" succ id "+successor_id);
                        Log.e(TAG, "succ only "+successor+" succ id "+successor_id);
                    }
                    // to update predeccesor info
                    else if (msg.contains(pred_only)) {
                        String[] values = msg.split("-");
                        // fill succ and pred info
                        predecessor = values[1];
                        predecessor_id = values[2];
                        System.out.println("only pred is "+predecessor+" pred id "+predecessor_id);
                        Log.e(TAG, "only pred is "+predecessor+" pred id "+predecessor_id);
                    }

                    else if(msg.contains(msg_info)){
                        // Request to insert message
                        String[] Messages = msg.split(split_break);

                        add_message(Messages[1],Messages[2]);

                    }

                    else if(msg.contains(send_data)){
                        // if data send request comes
                        // Send_Data@#$Port_ID
                        String[] msgs = msg.split(split_break);

                        if(msgs[1].contains(myPort)){
                            // make global flag true
                            global_flag = true;
                        }
                        else{
                            MsgToSend = map_data + split_new+"\n";
                            // Send all map data to port id in msgs[1]
                           for(String key: file_map.keySet()){
                               MsgToSend = MsgToSend + key + split + file_map.get(key)+ split_new+ "\n";

                           }
                            // Send all data to requestor node
                            send_request(msgs[1], MsgToSend);
                            Log.e(TAG, "Query Message sent to Node ID "+msgs[1]+" "+MsgToSend);
                            // Tell successor node to do the same
                            sleep(500);
                            send_request(successor_id,msg);

                        }

                    }

                    else if(msg.contains(map_data)){
//                        Split the files data using new splitter

                        String[] mappings = msg.split(split_new_break);

                        for(int i =1; i<mappings.length; i++){

                            String[] key_val = mappings[i].split(split_break);
                            all_map.put(key_val[0], key_val[1]);
                        }

                    }

                    else if(msg.contains(find_key)){
                        // Find+split+key+port
                        String[] find_msg = msg.split(split_break);

                        if(file_map.containsKey(find_msg[1])){
                            String value = file_map.get(find_msg[1]);
                            value = found_val + split+ value;
                            Log.e(TAG, "find_msg[1] "+find_msg[1]);
                            send_request(find_msg[2].trim(),value);
                        }
                        else{
                            send_request(successor_id,msg);
                        }


                    }

                    else if(msg.contains(found_val)){
                        String[] values = msg.split(split_break);
                        found_value = values[1];
                        global_flag = true;

                    }
                    else if(msg.contains(delete_data)){
                        // delete entire data
//                        delete_data + split + myPort;
                        String[] delete_val = msg.split(split_break);

                        if(!delete_val[1].contains(myPort)){

                            file_map.clear();
                            send_request(successor_id,msg);

                        }

                    }
                    else if(msg.contains(delete_key)){

                        String[] delete_val = msg.split(split_break);

                        if(file_map.containsKey(delete_val[1])){
                            file_map.remove(delete_val[1]);
                        }
                        else{
                            send_request(successor_id,msg);

                        }


                    }

                    Log.e(TAG, "my succ is "+successor+" my pred is "+predecessor+" " +
                            " my succ id "+successor_id+" my pred id "+predecessor_id);

                } catch (SocketTimeoutException e) {
                    Log.e(TAG, "ServerTask Socket TimeoutException");
                    System.out.println("ServerTask Socket TimeoutException");
                } catch (SocketException e) {
                    Log.e(TAG,"ServerTask Socket Exception");
                    System.out.println("ServerTask Socket Exception");
                } catch (EOFException e) {
                    Log.e(TAG,"ServerTask Socket EOFException");
                    System.out.println("ServerTask Socket EOFException");
                } catch (IOException e) {
                    Log.e(TAG, "ServerTask IOException");
                } catch (Exception e) {
                    Log.e(TAG, "ServerTask socket Exception");
                    e.printStackTrace();
                }

            }

        }

        public void send_request(String port_number, String message){
            try {
                Log.e(TAG, "node id is "+port_number);
                Socket new_socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(port_number));

                Log.e(TAG, "node id is "+port_number);

                OutputStreamWriter out = new OutputStreamWriter(new_socket.getOutputStream());
                BufferedWriter bw = new BufferedWriter(out);
                bw.write(message);
                bw.flush();
                sleep(250);
                System.out.println("message sent for successor and predecessor or for query" + message);
                bw.close();
                new_socket.close();
            }
            catch (Exception e) {
                System.out.println("ClientTask Socket Exception");
                e.printStackTrace();
            }

        }

    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {

            String msg = "";

            for (int j = 0; j < msgs.length - 1; j++)
                msg = msg + msgs[j] + "\n";

            String myPort_Number = msgs[msgs.length -1];

            try {

                //System.out.println("Message is "+msg+" my port number is "+myPort_Number);
                Log.e(TAG, "Message is "+msg+" my port number is "+myPort_Number);

                // Check if message contains the node id "11108" for join request

                if (msg.contains("11108")) {

                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt("11108"));

                    // Add identifier to message before sending
                    String MsgToSend = node_join + '-' + myPort_Number;
                    OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());
                    BufferedWriter bw = new BufferedWriter(out);
                    bw.write(MsgToSend);
                    bw.flush();
                    sleep(500);
                    System.out.println("message sent " + MsgToSend);
                    Log.e(TAG, "message sent " + MsgToSend);
                    bw.close();
                    socket.close();
                }
            }
                catch (SocketTimeoutException e){
                    System.out.println("ClientTask Socket TimeoutException");
                }catch (SocketException e) {
                    System.out.println("ClientTask Socket Exception");
                }
                catch (EOFException e) {
                    System.out.println("ClientTask Socket EOFException");
                }catch (IOException e) {
                    System.out.println("ClientTask Socket IOException");
                }catch (Exception e) {
                    Log.e(TAG, "ClientTask socket Exception");
                    System.out.println("ClientTask Socket Exception");
                }


        return null;
        }
    }


    private class ClientInsert extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {

            String msg = "";

            for (int j = 0; j < msgs.length - 1; j++)
                msg = msg + msgs[j] + "\n";

            String SendToPort = msgs[msgs.length -1];

            try {

                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(SendToPort));

                    // Add identifier to message before sending
                    String MsgToSend = msg_info + split + msg;
                    OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());
                    BufferedWriter bw = new BufferedWriter(out);
                    bw.write(MsgToSend);
                    bw.flush();
                    sleep(500);
                    Log.e(TAG, "message sent " + MsgToSend);
                    bw.close();
                    socket.close();

            }
            catch (SocketTimeoutException e){
                System.out.println("ClientTask Socket TimeoutException");
            }catch (SocketException e) {
                System.out.println("ClientTask Socket Exception");
            }
            catch (EOFException e) {
                System.out.println("ClientTask Socket EOFException");
            }catch (IOException e) {
                System.out.println("ClientTask Socket IOException");
            }catch (Exception e) {
                Log.e(TAG, "ClientTask socket Exception");
            }


            return null;
        }
    }


    private class ClientRequest extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {

            String msg = "";

            for (int j = 0; j < msgs.length - 1; j++)
                msg = msg + msgs[j] + "\n";

            String SendToPort = msgs[msgs.length -1];

            try {

                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(SendToPort));

                OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());
                BufferedWriter bw = new BufferedWriter(out);
                bw.write(msg);
                bw.flush();
                sleep(500);
                Log.e(TAG, "message sent " + msg);
                bw.close();
                socket.close();

            }
            catch (SocketTimeoutException e){
                System.out.println("ClientTask Socket TimeoutException");
            }catch (SocketException e) {
                System.out.println("ClientTask Socket Exception");
            }
            catch (EOFException e) {
                System.out.println("ClientTask Socket EOFException");
            }catch (IOException e) {
                System.out.println("ClientTask Socket IOException");
            }catch (Exception e) {
                Log.e(TAG, "ClientTask socket Exception");
            }


            return null;
        }
    }

}
