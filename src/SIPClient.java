import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class SIPClient {

	// no spaces in URI!!
	private final String myURI = "sip:DonkeyKong@mario.kart";
	private final String myIP = "127.0.0.1";
	private final int myPort = 5060;
	private String remoteIP;
	private int remotePort;
	
	private final int millisToWaitForError = 2000; 
	
	private Socket server; 
	private PrintWriter out;
	private BufferedReader in;
	
	private Socket remote;
	private PrintWriter remoteOut;
	private BufferedReader remoteIn;
	
	private ServerListener serverListener;
	
	public SIPClient () {}
	
	// call from GUI when you enter the server IP
	public void connectToServer(String host, int port) {
		try {
			// open socket, reader, writer
			server = new Socket(host, port);
			out = new PrintWriter(server.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(server.getInputStream()));
			
			// register with server
			out.write("REGISTER "+myURI+"\n");
			out.flush();
			
			// registration successful?
			String response = in.readLine();
			if (response.contains(CodeUtil.OK)) {
				System.out.println("Connected & registered");
				serverListener = new ServerListener();
				serverListener.start();
			}
			else {
				System.out.println("Could not register "+myURI+"\nDisconnecting...");
				// can't just call disconnect - if there was a conflict with the name, 
				// the server would remove the existing client from its table
				out.close();
				in.close();
				server.close();
				server = null;
			}
		} 
		catch (IOException e) {
			System.out.println("Error connecting to server!");
			return;
		}
	}
	
	// disconnects from current server
	public void disconnectFromServer() {
		if (server == null) return;
		out.write("DISCONNECT "+myURI+"\n");
		out.flush();
		try {
			out.close();
			in.close();
			server.close();
		} catch (IOException e) {}
		
		server = null;
	}
	
	// establishes a VoIP connection with calleeURI
	// callee should provide its IP & port as optional params appended to the 200-OK message
	// caller should provide its IP & port as optional params appended to the ACK message
	public void call(String calleeURI) {
		String response;
		try {
			// pause server listener so it doesn't eat up our responses from the sever
			serverListener.pause();
			
			// invite ->
			out.write("INVITE "+calleeURI+"\n");
			out.flush();
			
			// OK <-
			response = in.readLine();
			if (response.contains(CodeUtil.OK)) {
				String[] banana = response.split(" ");
				remoteIP = banana[3];
				remotePort = Integer.valueOf(banana[4]);
				
				// ACK ->
				out.write("ACK "+calleeURI+" "+myIP+" "+myPort+"\n");
				out.flush();
				
				// error CODE <- ?
				response = "";
				server.setSoTimeout(millisToWaitForError);
				try {
					response = in.readLine();
				} catch (SocketTimeoutException e) {}
				server.setSoTimeout(0);
				
				// restore server listener
				serverListener.unpause();
				
				if (response.length() > 0) {
					System.out.println("Error occured sending ACK - "+response.split(" ")[2]);
					return;
				}
				
				
				// TODO establish direct connection to remoteIP:remotePort
				
				
			}
			else {
				System.out.println("Unable to call "+calleeURI+" - "+response.split(" ")[2]);
			}
		}
		catch (IOException e) {
			System.out.println("Error talking to server!");
		}
		// restore server listener if we haven't already
		serverListener.unpause();
	}
	
	// establishes a VoIP connection with caller
	// callee should provide its IP & port as optional params appended to the 200-OK message
	// caller should provide its IP & port as optional params appended to the ACK message
	public void pickUp(String callerURI) {
		String response;
		try {
			serverListener.pause();
			out.write("CODE "+callerURI+CodeUtil.OK+" "+myIP+" "+myPort+"\n");
			
			response = in.readLine();
			if (response.startsWith("ACK")) {
				String[] banana = response.split(" ");
				remoteIP = banana[2];
				remotePort = Integer.valueOf(banana[3]);
				
				// restore server listener
				serverListener.unpause();
				
				
				// TODO establish direct connection to remoteIP:remotePort
				
				
			}
			else {
				System.out.println("Error occured sending 200-OK - "+response.split(" ")[2]);
			}
		}
		catch (IOException e) {
			System.out.println("Error talking to server!");
		}
		// restore server listener if we haven't already
		serverListener.unpause();
	}
	
	public void closeCall() {
		// TODO in call create serversocket, in pickUp create socket, connect to caller 
		// TODO another listener thread
		// TODO send BYE to remote, receive 200-OK
		
		// TODO handle hangup, cancel, busy
	}
	
	private class ServerListener extends Thread {
		
		private boolean isPaused = false;
		
		public ServerListener() {}

		public synchronized void pause(){
		    isPaused = true;
		    while(isPaused) {
				   try {
					   wait();
				   } catch (InterruptedException e) {}
			   }
		}

		public synchronized void unpause(){
		   isPaused = false;
		   notifyAll();
		}
		
		
		@Override
		public void run() {
			while (true) {
				String line = "";
				try {
					line = in.readLine();
				}
				catch (IOException e) {
					System.out.println("Error reading from server!");
				}
				
				if (line.startsWith("INVITE")) {
					
					// TODO enable the pick up button or make a popup window or something
					
				}
				else {
					System.out.println("Unexpected message from server: "+line);
				}
			}
		}
	}
	
	public static void main(String[] args) {
		
	}
}
