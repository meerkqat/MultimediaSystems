import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class SIPServer {
	
	private int port = 1234;
	private HashMap<String, Socket> clients;
	
	public SIPServer() {
		clients = new HashMap<>();
		
		ServerSocket servSoc = null;
		try {
			servSoc = new ServerSocket(port);
		} 
		catch (IOException e) {
			System.out.println("Error opening server socket!");
		}
		
		while (true) {
			try {
				Socket s = servSoc.accept();
				Thread handler = new ConnectionHandler(s);
				handler.start();
			}
			catch (IOException e) {
				System.out.println("Error connecting to socket!");
			}
		}
	}
	
	
	private class ConnectionHandler extends Thread {
		private Socket client;
		private PrintWriter out;
		private BufferedReader in;
		
		public ConnectionHandler (Socket s){
			client = s;
			try { 
				out = new PrintWriter(s.getOutputStream(), true);
				in = new BufferedReader(new InputStreamReader(s.getInputStream()));
			}
			catch (IOException e) {
				System.out.println("Failed to open channel to socket");
			}
		}
		
		// connect to client, send opcode + arguments (space separated)
		// "REGISTER client@sip.whatever"
		// "INVITE client@sip.stuff"
		// "CODE client@sip.blerf 200"
		// "ACK client@sip.beepboop"
		public void run() {
			String line = "";
			try { 
				line = in.readLine();
			}
			catch (IOException e) {
				System.out.println("Error reading from socket!");
			}
			
			String[] banana = line.split(" ");
			if(banana[0].equals("REGISTER")) {
				register(banana[1]);
			}
			else if (banana[0].equals("INVITE") || banana[0].equals("CODE") || banana[0].equals("ACK")) {
				forward(banana[1], line);
			}
			
		}
		
		public void register(String id) {
			clients.put(id, client);
			out.write("CODE 200");
			out.flush();
		}
		
		public void forward(String id, String msg) {
			Socket dest = clients.get(id);
			try {
				PrintWriter pw = new PrintWriter(dest.getOutputStream(), true);
				pw.write(msg);
				pw.flush();
			}
			catch (IOException e) {
				System.out.println("Error opening out stream to destination!");
			}
		}
	}
	
	public static void main(String[] args) {
		new SIPServer();
	}
}
