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
	
	@SuppressWarnings("resource")
	public SIPServer() {
		clients = new HashMap<>();
		
		ServerSocket servSoc = null;
		try {
			servSoc = new ServerSocket(port);
		} 
		catch (IOException e) {
			System.out.println("Error opening server socket!");
		}
		
		System.out.println("Starting server");
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
	
	// get key by value from clients hashmap
	private String getIdFromSocket(Socket value) {
		for (String id : clients.keySet()) {
			if (clients.get(id).equals(value)) {
				return id;
			}
		}
		return null;
	}
	
	// handles any new connection
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
			System.out.println("Accepted client");
		}
		
		// start listening to client -> get opcode + arguments (space separated)
		// "REGISTER sip:from@domain.tld"
		// "INVITE sip:to@domain.tld"
		// "CODE sip:to@domain.tld CodeUtil.CODE"
		// "ACK sip:to@domain.tld"
		// "DISCONNECT sip:from@domain.tld"
		// optional additional params can be added after what is specified above (again space separated)
		public void run() {
			while (true) {
				// read command
				String line = "";
				try { 
					line = in.readLine();
				}
				catch (IOException e) {
					System.out.println("Error reading from socket!");
					interrupt();
				}
				
				String[] banana = line.split(" ");
				// register new client
				if(banana[0].equals("REGISTER")) {
					register(banana[1]);
				}
				// forward message to destination, swap receiver for sender
				else if (banana[0].equals("INVITE") || banana[0].equals("CODE") || banana[0].equals("ACK")) {
					banana[1] = getIdFromSocket(client);
					forward(banana[1], join(banana, " ")+"\n");
				}
				// client requested disconnect
				else if (banana[0].equals("DISCONNECT")) {
					clients.remove(banana[1]);
					try {
						out.close();
						in.close();
						client.close();
					} catch (IOException e) {}
					break;
				}
				// bad opcode
				else {
					out.write("CODE "+banana[1]+" "+CodeUtil.BadEvent+"\n");
					out.flush();
				}
			}
			
		}
		
		// register new client
		private void register(String id) {
			String response;
			if (clients.containsKey(id)) {
				response = "CODE "+id+" "+CodeUtil.Conflict+"\n";
			}
			else {
				clients.put(id, client);
				response = "CODE "+id+" "+CodeUtil.OK+"\n";
			}
			
			out.write(response);
			out.flush();
		}
		
		// forward msg to id
		private void forward(String id, String msg) {
			Socket dest = clients.get(id);
			
			// check if client with id exists
			if (!clients.containsKey(id)) {
				out.write("CODE "+id+" "+CodeUtil.NotFound+"\n");
				out.flush();
				return;
			}
			
			// forward to id
			try {
				PrintWriter pw = new PrintWriter(dest.getOutputStream(), true);
				pw.write(msg);
				pw.flush();
			}
			catch (IOException e) {
				System.out.println("Error opening out stream to destination!");
				out.write("CODE "+id+" "+CodeUtil.Gone+"\n");
				out.flush();
			}
		}
		
		// join string array into string
		private String join(String[] arr, String token) {
			StringBuilder sb = new StringBuilder();
			boolean first = true;
			for(String item : arr){
				if(!first || (first = false)) sb.append(token);
				sb.append(item);
			}
			return sb.toString();
		}
	}
	
	public static void main(String[] args) {
		new SIPServer();
	}
}
