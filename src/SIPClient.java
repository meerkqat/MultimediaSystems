import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class SIPClient {
	// TODO handle hangup
	
	// no spaces in URI!!
	private final String myURI = "sip:DonkeyKong@mario.kart";
	private final String myIP = "127.0.0.1";
	private final int myPort = 5060;
	private final int TCPPort = 2345;
	private String remoteIP;
	private int remotePort;
	
	public final int PREINIT = -1;
	public final int FREE = 0;
	public final int BUSY = 1;
	
	private int myState = PREINIT;
	
	private final int millisToWaitForError = 2000; 
	
	private Socket server; 
	private PrintWriter out;
	private BufferedReader in;
	
	private ServerSocket localServerSocket;
	private Socket remote;
	private PrintWriter remoteOut;
	private BufferedReader remoteIn;
	
	private ServerListener serverListener;
	private RemoteListener remoteListener;
	
	private AudioSIPGUI gui;
	
	public SIPClient () {
		gui = new AudioSIPGUI(this);
		
		System.out.println("Running client "+myURI);
	}
	
	// call from GUI when you enter the server IP
	public void connectToServer(String host, int port) {
		System.out.println("Connecting to SIP server");
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
				
				myState = FREE;
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
		System.out.println("Connected!");
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
		
		myState = PREINIT;
	}
	
	// returns the state of this client (free, calling, receiving call)
	public int getState() {
		return myState;
	}
	
	// establishes a VoIP connection with calleeURI
	// callee should provide its IP & port as optional params appended to the 200-OK message
	// caller should provide its IP & port as optional params appended to the ACK message
	public void call(String calleeURI) {
		myState = BUSY;
		
		System.out.println("Calling "+calleeURI);
		
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
				
				remoteListener = new LocalServer();
				remoteListener.run();
				
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
				System.out.println("Direct connection now");
				
				
				// TODO establish direct connection to remoteIP:remotePort
				
				
			}
			else if (response.contains(CodeUtil.RequestTerminated)) {
				System.out.println("Callee declined the call");
			}
			else if (response.contains(CodeUtil.BusyHere)) {
				System.out.println("Callee is busy");
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
		myState = BUSY;
		
		System.out.println("Receiving call from "+callerURI);
		
		String response;
		try {
			serverListener.pause();
			out.write("CODE "+callerURI+" "+CodeUtil.OK+" "+myIP+" "+myPort+"\n");
			
			response = in.readLine();
			if (response.startsWith("ACK")) {
				String[] banana = response.split(" ");
				remoteIP = banana[2];
				remotePort = Integer.valueOf(banana[3]);
				
				remoteListener = new RemoteListener();
				remoteListener.run();
				
				// restore server listener
				serverListener.unpause();
				
				System.out.println("Direct connection now");
				
				
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
	
	// decline current call
	public void declineCall(String callerURI) {
		out.write("CODE "+callerURI+" "+CodeUtil.RequestTerminated+"\n");
		out.flush();
	}
	
	// disconnect from current call
	public void closeCall() {
		remoteListener.endCall();
	}
	
	// listens to messages coming from the sip server (invites)
	private class ServerListener extends Thread {
		
		private boolean isPaused = false;
		
		public ServerListener() {}

		public synchronized void pause(){
			System.out.println("Pausing server listener");
		    isPaused = true;
		    while(isPaused) {
				   try {
					   wait();
				   } catch (InterruptedException e) {}
			   }
		}

		public synchronized void unpause(){
			System.out.println("Unpausing server listener");
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
				System.out.println("Server sent: "+line);
				
				if (line.startsWith("INVITE")) {
					String[] banana = line.split(" ");
					if (myState == BUSY) {
						out.write("CODE "+banana[1]+" "+CodeUtil.BusyHere+"\n");
						out.flush();
						continue;
					}

					gui.receivingCall(banana[1]);
					
				}
				else {
					System.out.println("Unexpected message from server: "+line);
				}
			}
		}
	}
	
	// thread that connects to the caller via a Socket to be able to send/receive the BYE signal
	private class RemoteListener extends Thread {
		protected boolean inCall = true;
		
		public RemoteListener() {
			try {
				remote = new Socket(remoteIP, TCPPort);
				remoteOut = new PrintWriter(remote.getOutputStream(), true);
				remoteIn = new BufferedReader(new InputStreamReader(remote.getInputStream()));
			}
			catch (IOException e) {
				System.out.println("Could not open connection to remote!");
			}
		}
		
		public void endCall() {
			inCall = false;
		}
		
		protected void doCall() {
			try {
				String msg;
				while (inCall) {
					msg = "";
					try {
						msg = remoteIn.readLine();
					} catch (SocketTimeoutException e) {}
					if (msg.startsWith("BYE")) {
						remoteOut.write("CODE "+CodeUtil.OK+"\n");
						remoteOut.flush();
						
						remoteIn.close();
						remoteOut.close();
						remote.close();
						localServerSocket.close();
						inCall = false;
					}
					else {
						System.out.println("Unexpected message from remote: "+msg);
					}
				}
				
				// we broke out of the while, but didn't get a BYE message, 
				// therefore we must be ending the call
				if(!remote.isClosed()) {
					remoteOut.write("BYE\n");
					remoteOut.flush();
					remote.setSoTimeout(0);
					msg = remoteIn.readLine();
					
					if (msg.contains(CodeUtil.OK)) {
						remoteIn.close();
						remoteOut.close();
						remote.close();
						localServerSocket.close();
					}
					else {
						System.out.println("Unexpected message from remote: "+msg);
					}
				}
				
				myState = FREE;
			}
			catch (IOException e) {
				System.out.println("Error occured talking to remote!");
			}
		}
		
		@Override
		public void run() {
			doCall();
		}
	}
	
	// a thread that runs a local server when making a call - used to send/receive BYE signal 
		private class LocalServer extends RemoteListener {
			
			// init server socket
			public LocalServer() {
				try {
					localServerSocket = new ServerSocket(TCPPort);
				} 
				catch (IOException E) {
					System.out.println("Error establishing server socket!");
				}
			}
			
			@Override
			public void run() {
				try {
					// open connection
					remote = localServerSocket.accept();
					remote.setSoTimeout(500);
					remoteOut = new PrintWriter(remote.getOutputStream(), true);
					remoteIn = new BufferedReader(new InputStreamReader(remote.getInputStream()));
					
					doCall();
				}
				catch (IOException e) {
					System.out.println("Error occured connecting to remote!");
				}
			}
		}
	
	public static void main(String[] args) {
		new SIPClient();
	}
}
