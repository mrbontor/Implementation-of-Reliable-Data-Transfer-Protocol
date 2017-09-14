
/**
 * 
 * filename: Command_Sender.java
 * 
 * version: 1.0 01/05/2017
 *
 *         revisions: Initial version
  * @author Parvathi Nair    pan7447
 *         
 * References: 1. http://www.programcreek.com/2009/02/java-convert-image-to-byte-array-convert-byte-array-to-image/
 * 			   2. http://www.coderpanda.com/java-socket-programming-transferring-java-object-through-socket-using-udp/
 * 			   3. http://stackoverflow.com/questions/4252294/sending-objects-across-network-using-udp-in-java
 */
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Set;

/*
 * This class is the Command_Sender, which is supposed to be in the shore and communicating with the buoy for images
 */
public class Command_Sender {
	Scanner sc;
	String dest_ip;
	String input;
	boolean flag;
	static HashMap<Integer, Packet> receivedPackets = new HashMap<Integer, Packet>();
	public static DatagramSocket serverSocket;
	public static DatagramPacket receivePacket;
	public static DatagramSocket clientSocket;
	public static DatagramPacket sendPacket;
	static int seqnum = 0;
	public static Packet packet = new Packet();
	FileOutputStream fos;

	public Command_Sender() {

		sc = new Scanner(System.in);
		flag = false;
		System.out.println("Enter the destination ip");
		dest_ip = sc.nextLine();
		try {
			fos = new FileOutputStream("C:/Users/Parvathi/workspace/cnProject2/src/outputtestimage2");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	/*
	 * This is an inner class which is responsible to send the packets from the
	 * shore to the Buoy
	 */
	private class Client extends Thread {
		private int dest_port;

		public Client() {
			dest_port = 9999;
		}

		public void run() {

			try {
				askForCommandsInClient();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		}

		/*
		 * This method sends the buoy message which has the image name the
		 * Command_Sender wants from the Buoy. Also it has 'y' or 'n' for other
		 * commands such as air temperaure, salinity and water temperature.
		 */
		private void askForCommandsInClient() throws IOException, InterruptedException {
			clientSocket = new DatagramSocket();
			byte[] sendData = new byte[2400];
			String sendDataString = sc.nextLine();
			sendData = sendDataString.getBytes();
			sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(dest_ip), dest_port);
			if (flag == true) {
				clientSocket.send(sendPacket);
				flag = false;
			}
		}

		/*
		 * This method sends the acknowledgement as and when the Command Sender
		 * receives any packets from the Buoy
		 */
		public void sendAcks() throws IOException {
			byte[] sendData = new byte[64002];
			sendData[0] = (byte) packet.seq_number;
			sendData[1] = (byte) packet.noMoreSegs;
			int packetPos = 2;
			for (int pos = 0; pos < packet.data.length; pos++) {
				sendData[packetPos++] = packet.data[pos];
			}
			System.out.println("Sending ACK: " + packet.seq_number);
			sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(dest_ip), dest_port);
			clientSocket.send(sendPacket);

		}
	}

	/*
	 * This is an inner class responsible to accept packets from the Buoy_Sender
	 */
	public class Server extends Thread {
		private int port;

		public Server() {
			port = 8888;
		}

		/*
		 * This method gets the commands form the Buoy_Sender. Basically
		 * handshake is done here.
		 */
		private void getCommandsInServer() throws IOException {
			serverSocket = new DatagramSocket(port);
			byte[] receiveData = new byte[2400];
			while (true) {
				receivePacket = new DatagramPacket(receiveData, receiveData.length);
				serverSocket.receive(receivePacket);
				String sentence = new String(receivePacket.getData());
				sentence = sentence.trim();
				input = sentence;
				System.out.println(input);
				flag = true;
				receivePacket = null;
				receiveData = new byte[2400];
				receivePacket = new DatagramPacket(receiveData, receiveData.length);
				serverSocket.receive(receivePacket);
				sentence = new String(receivePacket.getData());
				sentence = sentence.trim();
				input = sentence;
				System.out.println();
				System.out.println(input);
				System.out.println();
				System.out
						.println("**********************************************************************************");
				System.out.println();
				System.out
						.println("Commands received and processed. Connection established. Image can be received now");
				System.out.println();
				System.out
						.println("**********************************************************************************");
				break;

			}
		}

		/*
		 * This method accepts the packets sent by the Buoy_Sender
		 */
		private void receivePackets() throws IOException, ClassNotFoundException {
			byte[] receiveData = new byte[64002];
			byte[] rcvdData;
			while (true) {
				receivePacket = new DatagramPacket(receiveData, receiveData.length);
				try {
					serverSocket.receive(receivePacket);
				} catch (IOException e) {
					e.printStackTrace();
				}
				rcvdData = receivePacket.getData();
				Packet pckt = new Packet();
				pckt.seq_number = rcvdData[0];
				System.out.println();

				pckt.noMoreSegs = rcvdData[1];
				int packetPos = 2;
				for (int pos = 0; pos < rcvdData.length - 2; pos++) {
					pckt.data[pos] = rcvdData[packetPos++];
				}
				packet.seq_number = (byte) (pckt.seq_number + 1);
				receivedPackets.put((int) pckt.seq_number, pckt);
				System.out.println();
				System.out.println("Received packet: " + pckt.seq_number);
				System.out.println();
				Client client = new Client();
				client.sendAcks();
				
				// Here it checks if the received packet is the last packet. If
				// yes it checks if it has all the packets
				if (pckt.noMoreSegs == 1) {				
					Set keys = receivedPackets.keySet();
					for (int k = 1; k < keys.size(); k++) {
						if (k - (k - 1) == 1) {
							continue;
						} else {
							System.out.println("all packets are not received");
							break;
						}
					}
					System.out.println(
							"**********************************************************************************");
					System.out.println();
					System.out.println("Received all packets");
					System.out.println();
					System.out.println(
							"**********************************************************************************");
					
					// here the data part of all packets are written in a file to recreate the image
					
					for (int i = 0; i < receivedPackets.size(); i++) {
						fos.write(receivedPackets.get(i).data);
					}
					fos.close();
				}
			}
		}

		public void run() {
			try {
				getCommandsInServer();
			} catch (SocketException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {

				receivePackets();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	/*
	 * This is the main method of the parent class Command_Server
	 */
	public static void main(String[] args) {
		Command_Sender command_server = new Command_Sender();
		Server server = command_server.new Server();
		server.start();
		Client client = command_server.new Client();
		client.start();
	}
}
