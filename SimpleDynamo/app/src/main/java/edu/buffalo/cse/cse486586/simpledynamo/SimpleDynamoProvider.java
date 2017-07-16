package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
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
import java.util.*;
import java.util.Formatter;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

import static android.content.ContentValues.TAG;
import static java.lang.Thread.sleep;


//******************************************************************************
// Port ID represents 11108
// Node ID represents SHA-1 String for comparison
//
//
//
//******************************************************************************
public class SimpleDynamoProvider extends ContentProvider {
	static final int SERVER_PORT = 10000;
	static Map<String, String> file_map = new HashMap<String, String>();
	static Map<String, String> all_map = new HashMap<String, String>();
	// Key MAp is used to store key values for Query function
	static Map<String, String> key_map = new HashMap<String, String>();
	// Key flag map is used to find if key is found or not
	static Map<String, Boolean> key_flag = new HashMap<String, Boolean>();
	static int node_index = -1;
	static String myPort = "";
	static final String[] REMOTE_PORT = {"11108", "11112", "11116", "11120", "11124"};
	static Node[] Node_list = new Node[5];
	static final String msg_insert = "msg_insert";
	static final String send_data = "send_data";
	static final String delete_data = "delete_data";
	static final String delete_key = "delete_key";
	static final String map_data = "map_data";
	static final String node_creation = "node_creation";
	static final String query_key = "query_key";
	static final String key_found = "key_found";
	static final String split = "@#$";
	static final String split_new = "&%12";
	static final String split_break = "[@][#][$]";
	static final String split_new_break = "[&][%][1][2]";
	static boolean global_flag = false;
	static boolean global_delete_flag = false;
	static boolean delete_key_flag = false;
	static boolean global_create_flag = true;
	static boolean global_insert_flag = true;
	static boolean global_key_flag = false;
	static int msg_send_counter = 0; // counter for sending messge to get * all data

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		// Delete data from all Nodes
		if(selection.contains("*")) {

			file_map.clear();
			global_delete_flag = false;
			String msg = delete_data + split + myPort;
			new ClientDelete().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);

			while (global_delete_flag == false);
		}

		else if (selection.contains("@")){

			file_map.clear();;
		}

		else{
			delete_key_flag = false;
			int send_index = get_index(selection);

			new DeleteKey().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, selection, send_index+"");
			while(delete_key_flag == false);

		}

		return 0;
	}

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public synchronized Uri insert(Uri uri, ContentValues values) {

		String KEY_FIELD = "key";
		String filename = "";
		String value = "";

		global_insert_flag = false;
		for (String str : values.keySet()) {
			if (str.equals(KEY_FIELD))
				filename = values.getAsString(str);
			else
				value = values.getAsString(str);

		}
		add_message(filename, value);

		while (global_insert_flag == false);
		return null;
	}

	@Override
	public boolean onCreate() {
		// Create a table for information about all the nodes in the chord
		global_create_flag = false;
		// Create myport value for client
		TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
		String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
		myPort = String.valueOf((Integer.parseInt(portStr) * 2));

		try {

			ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
			new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
		}
		catch (IOException e) {
			Log.e(TAG, "Can't create a ServerSocket");

		}
		catch (Exception e) {
			Log.e(TAG, "OnCreate Exception for myPort");

		}

		Create_Chord(myPort);
		String msg = node_creation + split + myPort;
		new Node_Creation().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
		while(global_create_flag == false);
		return false;
	}

	@Override
	public synchronized Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {

		while(global_create_flag == false || global_insert_flag == false){
			Log.e(TAG, " Query wait time");
		}

		if(selection.contains("*")) {
			// Fetch all data from all Nodes
				all_map.clear();
				global_flag = false;
				String msg = send_data + split + myPort;
				new ClientRequest().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);

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
					Log.e(TAG, "Key and Value "+key+" "+all_map.get(key));
					cursor.addRow(values);
				}

				return cursor;

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

// 		If only key is required, then fetch the value
            key_map.put(selection, "");
			key_flag.put(selection, false);
			int send_index = -1;
			try{
				// Generate hash id for this message
				String msg_key = genHash(selection);

				if(msg_key.compareTo(Node_list[0].Node_id) <= 0
						|| msg_key.compareTo(Node_list[4].Node_id) > 0){

					send_index = 2;
				}
				else {
					for (int i = 1; i < Node_list.length; i++) {

						if (msg_key.compareTo(Node_list[i].Node_id) <= 0 ){

							send_index = (i+2)%5;
							break;
						}

					}
				}

				//  based on send index, Gossip the message to insert in 3 nodes
				new ClientQuery().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, selection, send_index+"");

				while(key_flag.get(selection) == false);
				String[] columns = {"key", "value"};

				MatrixCursor cursor = new MatrixCursor(columns);
				String[] values = {selection, key_map.get(selection)};
				Log.e(TAG, "query value returned "+key_map.get(selection));
				cursor.addRow(values);
				return cursor;


			}
			catch (Exception e) {
				Log.e(TAG, "Message hash conversion error");

			}

		}


		return null;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
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

	public void Create_Chord(String my_port) {

		try {
		String str = "";
			for (int i = 0; i < REMOTE_PORT.length; i++) {

			str = genHash(Integer.parseInt(REMOTE_PORT[i])/2+"");
			//                  Port ID abd Node_Id
			Node obj = new Node(REMOTE_PORT[i], str);

			Node_list[i] = obj;
			}

			Arrays.sort(Node_list, new Comp());

			for(int i=0; i<Node_list.length; i++) {
				if(Node_list[i].Port_id.equals(my_port)) {
					node_index = i;
					break;
				}

			}
		}
			catch(Exception e) {
				Log.e(TAG, "Error in SHA-1 conversion of ports");
			}
		}


	public int get_index(String key){

		int send_index = -1;
		try{
			// Generate hash id for this node
			String msg_value = genHash(key);

			if(msg_value.compareTo(Node_list[0].Node_id) <= 0
					|| msg_value.compareTo(Node_list[4].Node_id) > 0){

				send_index = 0;
			}
			else {
				for (int i = 1; i < Node_list.length; i++) {

					if (msg_value.compareTo(Node_list[i].Node_id) <= 0 ){

						send_index = i;
						break;
					}

				}
			}

		}
		catch (Exception e) {
			Log.e(TAG, "Message hash conversion error");

		}
		return send_index;

	}


	public void add_message(String key, String value){

		int send_index = -1;
		try{
			// Generate hash id for this node
			String msg_value = genHash(key);
            String msg = key+split+value;

				if(msg_value.compareTo(Node_list[0].Node_id) <= 0
					|| msg_value.compareTo(Node_list[4].Node_id) > 0){

					send_index = 0;
				}
				else {
					for (int i = 1; i < Node_list.length; i++) {

						if (msg_value.compareTo(Node_list[i].Node_id) <= 0 ){

							send_index = i;
							break;
						}

					}
				}

			//  based on send index, Gossip the message to insert in 3 nodes
			new ClientInsert().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, send_index+"");
		}
		catch (Exception e) {
			Log.e(TAG, "Message hash conversion error");

		}

	}

	// Server class
	private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

		@Override
		protected Void doInBackground(ServerSocket... sockets) {
			ServerSocket serverSocket = sockets[0];

			while (true) {
				try {
					String MsgToSend = "";
					Log.e(TAG, "Server Socket " + myPort);
					Socket socket = serverSocket.accept();
					String msg = "";
					Log.e(TAG, "Server Socket " + myPort);
					//socket.setSoTimeout(1000);
					BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					Log.e(TAG, "Server Socket aaa");
					while (true) {

						String msg_line = in.readLine();
						Log.e(TAG, "msg Line " + msg_line);
						if (msg_line == null || msg_line.isEmpty())
							break;
						msg = msg + msg_line;
					}
					//in.close();
					Log.e(TAG, "msg received " + msg);
					// Message insert request
					if(msg.contains(msg_insert)){

						// Insert the message<Key, Value>  in hashmap
						String[] key_value = msg.split(split_break);
						if(key_value.length > 2)
							file_map.put(key_value[1], key_value[2]);

					}
					// Request to send all the Map data
					else if(msg.contains(send_data)){
						// if data send request comes
						// Send_Data@#$Port_ID
						String[] msgs = msg.split(split_break);

							MsgToSend = map_data + split_new;
							// Send all map data to port id in msgs[1]
							for(String key: file_map.keySet()){
								MsgToSend = MsgToSend + key + split + file_map.get(key)+ split_new;

							}

						OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());

						BufferedWriter bw = new BufferedWriter(out);
						bw.write(MsgToSend);
						//Log.e(TAG, "key found Msg "+key_found+split+file_map.get(query_msgs[1]));

						bw.newLine();
						bw.flush();
						sleep(250);
						bw.close();
						out.close();

							// Send all data to requestor node
							//send_request(msgs[1], MsgToSend);
							//Log.e(TAG, "Query Message sent to Node ID "+msgs[1]+" "+MsgToSend);


						}
					// Recieve all map data from Nodes and put into All Map
					else if(msg.contains(map_data)){

						msg_send_counter--;

//						Split the files data using new splitter

						String[] mappings = msg.split(split_new_break);

						for(int i =1; i<mappings.length; i++){

							String[] key_val = mappings[i].split(split_break);
							all_map.put(key_val[0], key_val[1]);
						}

						if(msg_send_counter == 0)
							global_flag = true;
					}
					// if query key is required then, send acknowledgement message too
					else if(msg.contains(query_key)){

						String[] query_msgs = msg.split(split_break);

						OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());

						BufferedWriter bw = new BufferedWriter(out);
						if(file_map.containsKey(query_msgs[1])){
							bw.write(key_found+split+file_map.get(query_msgs[1]));
							Log.e(TAG, "key found Msg "+key_found+split+file_map.get(query_msgs[1]));
						}
						else{
							bw.write("No_Key");
						}
						bw.newLine();
						bw.flush();
						//sleep(250);
						bw.close();
						out.close();
						Log.e(TAG, "Msg sent for query");
					}

					else if(msg.contains(delete_data)){
						file_map.clear();

					}

					else if(msg.contains(delete_key)){
						String[] query_msgs = msg.split(split_break);

						file_map.remove(query_msgs[1]);
					}

					else if(msg.contains(node_creation)){
						String[] msgs = msg.split(split_break);

						MsgToSend = map_data + split_new;
						msgs[1] = msgs[1].trim();
						// Send all map data to port id in msgs[1]
						for(String key: file_map.keySet()){

							int index = get_index(key);

							if(Node_list[index%5].Port_id.equals(msgs[1])
									|| Node_list[(index+1)%5].Port_id.equals(msgs[1])
									|| Node_list[(index+2)%5].Port_id.equals(msgs[1])) {

								MsgToSend = MsgToSend + key + split + file_map.get(key) + split_new;
								Log.e(TAG, "Key Value added for new Node "+key);
							}

						}

						OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());

						BufferedWriter bw = new BufferedWriter(out);
						bw.write(MsgToSend);
						//Log.e(TAG, "key found Msg "+key_found+split+file_map.get(query_msgs[1]));

						bw.newLine();
						bw.flush();
						sleep(250);
						bw.close();
						out.close();

					}


				}
				catch (SocketTimeoutException e) {
					Log.e(TAG, "ServerTask Socket TimeoutException");
				} catch (SocketException e) {
					Log.e(TAG,"ServerTask Socket Exception");
				} catch (EOFException e) {
					Log.e(TAG,"ServerTask Socket EOFException");
				} catch (IOException e) {
					Log.e(TAG, "ServerTask IOException");
				} catch (Exception e) {
					Log.e(TAG, "ServerTask socket Exception");
				}
			}
		}
		public void send_request(String port_number, String message){
			try {
				Log.e(TAG, "node id is "+port_number);
				Socket new_socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
						Integer.parseInt(port_number));

				OutputStreamWriter out = new OutputStreamWriter(new_socket.getOutputStream());
				BufferedWriter bw = new BufferedWriter(out);
				bw.write(message);
				bw.flush();
				sleep(250);
				System.out.println("message sent for all MAP Data" + message);
				bw.close();
				new_socket.close();
			}
			catch (Exception e) {
				System.out.println("ClientTask Socket Exception");
				e.printStackTrace();
			}

		}
	}

	private class ClientInsert extends AsyncTask<String, Void, Void> {

		@Override
		protected Void doInBackground(String... msgs) {

			String msg = "";

			for (int j = 0; j < msgs.length - 1; j++)
				msg = msg + msgs[j] + "\n";

			int send_index = Integer.parseInt(msgs[msgs.length -1]);
			for(int i = send_index; i <= send_index +2; i++) {
				String SendToPort = Node_list[i%5].Port_id;

				try {
					Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
							Integer.parseInt(SendToPort));

					//socket.setSoTimeout(250);
					// Add identifier to message before sending
					String MsgToSend = msg_insert + split + msg;
					OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());
					BufferedWriter bw = new BufferedWriter(out);
					bw.write(MsgToSend);
					bw.flush();
					sleep(250);
					Log.e(TAG, "message sent " + MsgToSend);
					bw.close();
					socket.close();

				} catch (SocketTimeoutException e) {
					System.out.println("ClientTask Socket TimeoutException");
				} catch (SocketException e) {
					System.out.println("ClientTask Socket Exception");
				} catch (EOFException e) {
					System.out.println("ClientTask Socket EOFException");
				} catch (IOException e) {
					System.out.println("ClientTask Socket IOException");
				} catch (Exception e) {
					Log.e(TAG, "ClientTask socket Exception");
				}

			}
			global_insert_flag = true;
			return null;
		}
	}


	private class ClientRequest extends AsyncTask<String, Void, Void> {

		@Override
		protected Void doInBackground(String... msgs) {

			String msg = "";
			//msg_send_counter =0;
			for (int j = 0; j < msgs.length - 1; j++)
				msg = msg + msgs[j] + "\n";

			for(int i = 0; i <= 4; i++) {
				String SendToPort = Node_list[i%5].Port_id;

				if(SendToPort.equals(myPort))
					continue;
				try {
					Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
							Integer.parseInt(SendToPort));
					//socket.setSoTimeout(250);
					// Add identifier to message before sending
					String MsgToSend = msg;
					OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());
					BufferedWriter bw = new BufferedWriter(out);
					bw.write(MsgToSend);
					bw.newLine();
					bw.flush();
					sleep(250);
					Log.e(TAG, "message sent " + MsgToSend);

					//msg_send_counter += 1;

					BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					String msgin ="";
					Log.e(TAG, "Reading msg");
					while (true) {
						msgin =  in.readLine();
						Log.e(TAG, msgin);
						if (msgin != null && !msgin.isEmpty()) {
							break;
						}
					}

					//Split the files data using new splitter

					String[] mappings = msgin.split(split_new_break);

					for(int j =1; j<mappings.length; j++){

						String[] key_val = mappings[j].split(split_break);
						all_map.put(key_val[0], key_val[1]);
					}

					bw.close();
					out.close();
					socket.close();

				} catch (SocketTimeoutException e) {
					System.out.println("ClientTask Socket TimeoutException");
				} catch (SocketException e) {
					System.out.println("ClientTask Socket Exception");
				} catch (EOFException e) {
					System.out.println("ClientTask Socket EOFException");
				} catch (IOException e) {
					System.out.println("ClientTask Socket IOException");
				} catch (Exception e) {
					Log.e(TAG, "ClientTask socket Exception");
				}

			}

			global_flag = true;
			return null;
		}
	}

	private class ClientQuery extends AsyncTask<String, Void, Void> {

		@Override
		protected Void doInBackground(String... msgs) {

			String msg = "";

			for (int j = 0; j < msgs.length - 1; j++)
				msg = msg + msgs[j];

			int send_index = Integer.parseInt(msgs[msgs.length -1]);
			for(int i = send_index; i >= send_index -2; i--) {
				String SendToPort = Node_list[(i+5)%5].Port_id;

				try {
					sleep(250);
					Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
							Integer.parseInt(SendToPort));

					// socket.setSoTimeout(1000);
					// Add identifier to message before sending
					String MsgToSend = query_key + split + msg+"\n";
					OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());
					BufferedWriter bw = new BufferedWriter(out);
					bw.write(MsgToSend);
					bw.newLine();
					bw.flush();
					//sleep(250);
					//sleep(250);
					Log.e(TAG, "message sent " + MsgToSend);
					Log.e(TAG, "message sent to Port number " + SendToPort);
				//	bw.close();

					BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					String msgin ="";
					Log.e(TAG, "Reading msg");
					while (true) {
						msgin = in.readLine();
						Log.e(TAG, msgin);
						if (msgin != null && !msgin.isEmpty()) {
							break;
						}
					}

					if(msgin.contains(key_found)){
						String key_msgs[] = msgin.split(split_break);
						key_map.put(msg, key_msgs[1]);
						key_flag.put(msg, true);
						break;
					}
                    in.close();

					out.close();
					socket.close();

				} catch (SocketTimeoutException e) {
					System.out.println("ClientTask Socket TimeoutException");
				} catch (SocketException e) {
					System.out.println("ClientTask Socket Exception");
				} catch (EOFException e) {
					System.out.println("ClientTask Socket EOFException");
				} catch (IOException e) {
					System.out.println("ClientTask Socket IOException");
				} catch (Exception e) {
					Log.e(TAG, "ClientTask socket Exception");
				}

			}
			return null;
		}
	}

	private class ClientDelete extends AsyncTask<String, Void, Void> {

		@Override
		protected Void doInBackground(String... msgs) {

			String msg = "";
			for (int j = 0; j < msgs.length - 1; j++)
				msg = msg + msgs[j] + "\n";

			for(int i = 0; i <= 4; i++) {
				String SendToPort = Node_list[i%5].Port_id;

				if(SendToPort.equals(myPort))
					continue;
				try {
					Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
							Integer.parseInt(SendToPort));
					//socket.setSoTimeout(250);
					// Add identifier to message before sending
					String MsgToSend = msg;
					OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());
					BufferedWriter bw = new BufferedWriter(out);
					bw.write(MsgToSend);
					bw.flush();
					sleep(250);
					Log.e(TAG, "message sent " + MsgToSend);
					bw.close();
					socket.close();

				} catch (SocketTimeoutException e) {
					System.out.println("ClientTask Socket TimeoutException");
				} catch (SocketException e) {
					System.out.println("ClientTask Socket Exception");
				} catch (EOFException e) {
					System.out.println("ClientTask Socket EOFException");
				} catch (IOException e) {
					System.out.println("ClientTask Socket IOException");
				} catch (Exception e) {
					Log.e(TAG, "ClientTask socket Exception");
				}

			}
			global_delete_flag = true;
			return null;
		}
	}

	private class DeleteKey extends AsyncTask<String, Void, Void> {

		@Override
		protected Void doInBackground(String... msgs) {

			String msg = "";

			for (int j = 0; j < msgs.length - 1; j++)
				msg = msg + msgs[j] + "\n";

			int send_index = Integer.parseInt(msgs[msgs.length -1]);
			for(int i = send_index; i <= send_index +2; i++) {
				String SendToPort = Node_list[i%5].Port_id;

				try {
					Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
							Integer.parseInt(SendToPort));

					//socket.setSoTimeout(250);
					// Add identifier to message before sending
					String MsgToSend = delete_key + split + msg;
					OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());
					BufferedWriter bw = new BufferedWriter(out);
					bw.write(MsgToSend);
					bw.flush();
					sleep(250);
					Log.e(TAG, "message sent " + MsgToSend);
					bw.close();
					socket.close();

				} catch (SocketTimeoutException e) {
					System.out.println("ClientTask Socket TimeoutException");
				} catch (SocketException e) {
					System.out.println("ClientTask Socket Exception");
				} catch (EOFException e) {
					System.out.println("ClientTask Socket EOFException");
				} catch (IOException e) {
					System.out.println("ClientTask Socket IOException");
				} catch (Exception e) {
					Log.e(TAG, "ClientTask socket Exception");
				}

			}
			delete_key_flag = true;
			return null;
		}
	}
	private class Node_Creation extends AsyncTask<String, Void, Void> {

		@Override
		protected Void doInBackground(String... msgs) {

			String msg = "";
			for (int j = 0; j < msgs.length - 1; j++)
				msg = msg + msgs[j] + "\n";

			for(int i = 0; i <= 4; i++) {

				String SendToPort = Node_list[i%5].Port_id;

				if(SendToPort.equals(myPort))
					continue;
				try {
					sleep(250);
					Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
							Integer.parseInt(SendToPort));
					//socket.setSoTimeout(250);
					// Add identifier to message before sending
					String MsgToSend = msg;
					OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());
					BufferedWriter bw = new BufferedWriter(out);
					bw.write(MsgToSend);
					bw.newLine();
					bw.flush();
					//sleep(250);
					Log.e(TAG, "message sent " + MsgToSend);

					BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
					String msgin ="";
					Log.e(TAG, "Reading msg");
					while (true) {
						msgin =  in.readLine();
						Log.e(TAG, msgin);
						if (msgin != null && !msgin.isEmpty()) {
							break;
						}
					}

					//Split the files data using new splitter

					String[] mappings = msgin.split(split_new_break);

					for(int j =1; j<mappings.length; j++){

						String[] key_val = mappings[j].split(split_break);
						file_map.put(key_val[0], key_val[1]);
					}

					bw.close();
					out.close();
					socket.close();

				} catch (SocketTimeoutException e) {
					System.out.println("ClientTask Socket TimeoutException");
				} catch (SocketException e) {
					System.out.println("ClientTask Socket Exception");
				} catch (EOFException e) {
					System.out.println("ClientTask Socket EOFException");
				} catch (IOException e) {
					System.out.println("ClientTask Socket IOException");
				} catch (Exception e) {
					Log.e(TAG, "ClientTask socket Exception");
				}

			}

			global_create_flag = true;

			return null;
		}
	}

	}

