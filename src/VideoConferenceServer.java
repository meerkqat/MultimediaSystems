import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

// activity list:
// client is streaming video to some address via udp
// client clicks join
// client opens socket to server
// client sends its stream (multicast) address
// client starts listening to server for stream addresses 
// server sends the address to all previously connected clients and simultaneously removes dead connections
// server sends all addresses of previously connected clients to new client

// !! client should be able to handle dead multicast connections (probably) !!

public class VideoConferenceServer {
	private int port = 1234;
	private ServerSocket ssocket;
	private HashMap<Socket,String> connections;
	
	
	public VideoConferenceServer() {
		connections = new HashMap<>();
		try {
			ssocket = new ServerSocket(port);
		}
		catch (IOException e) {
			System.out.println("Error opening server socket!");
			e.printStackTrace();
			System.exit(1);
		}
		
		System.out.println("Server listening for clients...");
		
		// accept new clients
		while (1 <3 /*cookies*/) {
			try {
				Socket socket = ssocket.accept();
				Thread handler = new ConnectionHandler(socket);
				handler.start();
			}
			catch (IOException e) {
				System.out.println("Error accepting socket!");
				e.printStackTrace();
			}
			
		}
	}
	
	// handles a client socket (one per thread)
	private class ConnectionHandler extends Thread {
		
		private Socket socket;
		private PrintWriter out;
		private BufferedReader in;
		boolean threadIsValid = true;
		
		public ConnectionHandler(Socket s) {
			System.out.println("New connection");
			
			// open connection
			socket = s;
			try { 
				out = new PrintWriter(s.getOutputStream(), true);
				in = new BufferedReader(new InputStreamReader(s.getInputStream()));
			}
			catch (IOException e) {
				System.out.println("Failed to open channel to socket");
				e.printStackTrace();
				threadIsValid = false;
			}
		}
		
		@Override
		public synchronized void run() {
			if (!threadIsValid) interrupt(); 
			System.out.println("Handling connection...");
			
			String address = "";
			
			// get stream address from socket
			try {
				address = in.readLine();
			}
			catch (IOException e) {
				System.out.println("Failed to read from socket");
				e.printStackTrace();
				interrupt();
			}
			
			// send new socket to all previous
			for (Socket s : connections.keySet()) { 
				try {
					PrintWriter o = new PrintWriter(s.getOutputStream(), true);
					o.write(address+"\n");
					o.flush();
					
					// send all previous to new socket
					out.write(connections.get(s)+"\n");
					out.flush();
				}
				catch (IOException e) { 
					// if any sockets died remove from list
					System.out.println("Obtaining outstream failed");
					connections.remove(s);
				}
			}
			
			// add new socket to list
			connections.put(socket, address);
			
			System.out.println("Connection handled.");
		}
	}
	
	public static void main(String[] args) {
		new VideoConferenceServer();
	}
}
