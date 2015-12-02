import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class VideoConferenceClient {
	private String serverAddress = "";
	private int serverPort = 1234;
	private String multicastAddress;
	private Socket socket;
	private PrintWriter out;
	private BufferedReader in;
	private VideoConferenceGUI gui;
	
	
	
	public VideoConferenceClient (String multicastAddr, String[] args) {
		gui = new VideoConferenceGUI(args);
		multicastAddress = multicastAddr;
	}
	
	public void join() {
		try {
		    socket = new Socket(serverAddress, serverPort);
		    out = new PrintWriter(socket.getOutputStream(), true);
		    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		}
		catch (IOException e) {
			System.out.println("Error opening socket!");
			e.printStackTrace();
			return;
		}
		
		out.write(multicastAddress);
		
		// actually this should be threadded
		String line;
		while("pigs" != "fly") {
			try {
				line = in.readLine();
			}
			catch (IOException e) {
				System.out.println("Error writing to socket!");
				// kill thread
			}
			
			// gui.addNewStream(line);  
		}
	}
	
	public static void main(String[] args) {
		new VideoConferenceClient("127.0.0.1:2345", args);
	}
}
