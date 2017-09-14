
/**
 * 
 * filename: Buoy_Sender.java
 * 
 * version: 1.0 05/01/2017
 *
 *         revisions: Initial version
 * @author Parvathi Nair    pan7447
 *         
 * References: 1. http://www.programcreek.com/2009/02/java-convert-image-to-byte-array-convert-byte-array-to-image/
 * 			   2. http://www.coderpanda.com/java-socket-programming-transferring-java-object-through-socket-using-udp/
 * 			   3. http://stackoverflow.com/questions/4252294/sending-objects-across-network-using-udp-in-java
 */
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.Scanner;

/*
 * This class behaves as Buoy. It accepts commands from the Command_Sender in shore and sends the response accordingly
 */
public class Buoy_Sender {

	Scanner sc;
	String dest_ip;
	int dest_port;
	static String imageName;
	static boolean flag = false;
	static ArrayList<Packet> packets = new ArrayList<Packet>();
	static ArrayList<Integer> acks = new ArrayList<Integer>();
	static int packet_num = 0;
	static long start_time;
	static long end_time;
	static double window = 1.0;
	public static DatagramPacket sendPacket;
	public static DatagramSocket clientSocket;
	public static DatagramSocket serverSocket;
	public static DatagramPacket receivePacket;
	static boolean congestionFlag = false;
	static double threshold = 0.0;
	static String salinity;
	static String airTemp;
	static String waterTemp;

	public Buoy_Sender() {
		sc = new Scanner(System.in);
		System.out.println("Enter the destination ip");
		dest_ip = sc.nextLine();
		dest_port = 8888;
	}

	/*
	 * This is an inner class Client which is responsible of sending packets to
	 * the shore
	 */
	private class Client extends Thread {

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
		 * This method asks the Command_Sender in shore for commands. It asks
		 * the Command_Sender if it wants to know the salinity, air temperature,
		 * water temperature and also what image file it wants. And once it gets
		 * 'y' or 'n' for all the commands and the the image name it sends back
		 * with appropriate response message.
		 * 
		 */

		private void askForCommandsInClient() throws IOException, InterruptedException {
			clientSocket = new DatagramSocket();
			byte[] sendData = new byte[2400];
			Random rand = new Random();
			int rand1 = rand.nextInt(50);
			int rand2 = rand.nextInt(100);
			int rand3 = rand.nextInt(100);
			if (flag == true) {
				StringBuffer sb = new StringBuffer();
				if (!salinity.equals("n")) {
					sb.append("Salinity is " + rand1 + " per thousand,");
				}
				if (!airTemp.equals("n")) {
					sb.append(" Air Temperature is " + rand2 + " degrees Fahrenheit,");
				}
				if (!waterTemp.equals("n")) {
					sb.append(" Water Temperature is " + rand3 + " degrees Fahrenheit,");
				}
				if (!imageName.equals("n")) {
					sb.append(" Sending ");
					sb.append(imageName);
				}
				String sentenceToSend = sb.toString();
				sendData = sentenceToSend.getBytes();
				sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(dest_ip), dest_port);
				clientSocket.send(sendPacket);
			} else if (flag == false) {
				String sendDataString = new String(
						"Enter 'y' or 'n' as per your requirements to the following commands: 1.salinity 2.air temperature 3.water temperature 4.Enter image name");
				sendData = sendDataString.getBytes();
				sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(dest_ip), dest_port);
				clientSocket.send(sendPacket);
			}

		}

		/*
		 * This method converts the image file into byte array and divides them
		 * into packets.
		 */
		public void createPackets(String imageName) throws FileNotFoundException {
			File file;
			BufferedImage img = null;
			Packet packet;
			int count = 0;
			System.out.println("Enter the path of the image file " + imageName);
			String filepath = sc.nextLine();
			byte[] bytes = imageToByte(filepath);
			int i = 0;
			int end = bytes.length - 64000;

			// here data is divided in bytes of length 64000 bytes
			while (i <= end) {
				packet = new Packet();
				packet.seq_number = (byte) count;
				int begin = i;
				int finish = i + 64000;
				packet.data = Arrays.copyOfRange(bytes, begin, finish);
				packets.add(packet);
				count++;
				i += 64000;
			}

			// the remaining bytes which doesn't form an exact 64000 bytes is
			// handled here
			int remaining = bytes.length % 64000;
			if (remaining > 0) {
				packet = new Packet();
				int begin = bytes.length - bytes.length % 64000;
				int finish = bytes.length;
				packet.data = Arrays.copyOfRange(bytes, begin, finish);
				packet.seq_number = (byte) count;
				packets.add(packet);

			}

			// the field noMoreSegs is set to 1 so that at the Command_Sender
			// side it comes to know when it received the last packet
			packets.get(packets.size() - 1).noMoreSegs = 1;

			// Once the packets are made, the sending of packets and accepting
			// of acknowledgments are run in two different threads parallely
			ServerAcceptAcks sap = new ServerAcceptAcks();
			sap.start();
			ClientSendPackets csp = new ClientSendPackets();
			csp.start();

		}

		/*
		 * This method converts the image file into byte array
		 */
		private byte[] imageToByte(String filepath) throws FileNotFoundException {
			File file = new File(filepath);
			FileInputStream fis = new FileInputStream(file);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] buf = new byte[1024];
			try {
				for (int readNum; (readNum = fis.read(buf)) != -1;) {
					bos.write(buf, 0, readNum);
				}
			} catch (IOException ex) {
			}
			byte[] bytes = bos.toByteArray();
			return bytes;
		}

	}

	/*
	 * This is an inner class which is responsible to accept packets from the
	 * shore
	 */
	private class Server extends Thread {
		private int port;

		public Server() {
			port = 9999;
		}

		/*
		 * This method accepts the commands from Command_Sender.
		 */
		private void getCommandsInServer() throws SocketException {
			serverSocket = new DatagramSocket(port);
			byte[] receiveData = new byte[2400];
			while (true) {
				receivePacket = new DatagramPacket(receiveData, receiveData.length);
				try {
					serverSocket.receive(receivePacket);
				} catch (IOException e) {
					e.printStackTrace();
				}
				String sentence = new String(receivePacket.getData());
				sentence = sentence.trim();
				String[] split = sentence.split(" ");
				salinity = split[0];
				airTemp = split[1];
				waterTemp = split[2];
				imageName = split[3];
				System.out.println();
				System.out.println(salinity + " " + airTemp + " " + waterTemp + " " + imageName);
				System.out.println();
				Client client = new Client();
				flag = true;
				try {
					client.askForCommandsInClient();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				try {
					client.createPackets(imageName);
				} catch (FileNotFoundException e1) {
					e1.printStackTrace();
				}
				break;
			}
		}

		public void run() {
			try {
				getCommandsInServer();
			} catch (SocketException e) {
				e.printStackTrace();
			}
		}
	}

	/*
	 * This class is responsible to send the packets to the shore.
	 */
	private class ClientSendPackets extends Thread {
		public void run() {
			try {
				sendPackets();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		/*
		 * This method runs until it has packets left to send. It send packets
		 * depending on the window size which is set in the variable window,
		 * initially set to 1
		 */
		private void sendPackets() throws IOException {
			while (packet_num >= 0 && packet_num < packets.size()) {
				byte[] sendData = new byte[64002];
				start_time = System.currentTimeMillis();
				System.out
						.println("**********************************************************************************");
				System.out.println("Window Size is    " + window);
				System.out
						.println("**********************************************************************************");

				for (int i = 0; i < window; i++) {
					if (packet_num < packets.size()) {
						sendData[0] = (byte) packets.get(packet_num).seq_number;
						sendData[1] = (byte) packets.get(packet_num).noMoreSegs;
						int packetPos = 2;
						for (int pos = 0; pos < packets.get(packet_num).data.length; pos++) {
							sendData[packetPos++] = packets.get(packet_num).data[pos];
						}

						System.out.println();
						System.out.println("Sending packet: " + packets.get(packet_num).seq_number);
						System.out.println();

						sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(dest_ip),
								dest_port);
						clientSocket.send(sendPacket);
						packet_num++;
					}
				}

				// The round trip time is assumed to be 500msec here. Each time
				// number of packets equivalent to the window size is sent, it
				// waits for acknowledgement
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {

					e.printStackTrace();
				}
			}
		}

		/*
		 * If any acknowledgement is missing, in that case this method is called
		 * so that the missing packet is retransmitted
		 */
		public void resendPackets(Integer integer) throws IOException {

			// minus 1, because the acknowledgement is always of the packet that
			// is expected.

			packet_num = integer.intValue() - 1;
			byte[] sendData = new byte[64002];
			sendData[0] = (byte) packets.get(packet_num).seq_number;
			sendData[1] = (byte) packets.get(packet_num).noMoreSegs;
			int packetPos = 2;
			for (int pos = 0; pos < packets.get(packet_num).data.length; pos++) {
				sendData[packetPos++] = packets.get(packet_num).data[pos];
			}
			System.out.println();
			System.out.println("Resending: " + new String("" + packets.get(packet_num).seq_number));
			System.out.println();
			sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(dest_ip), dest_port);
			clientSocket.send(sendPacket);
			packet_num++;

		}

	}

	/*
	 * This class is responsible for accepting acknowledgement packets from the
	 * Command_Sender
	 */
	private class ServerAcceptAcks extends Thread {
		public void run() {
			try {
				acceptAcks();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		/*
		 * This method accepts the acknowledgement packets
		 */
		private void acceptAcks() throws IOException, ClassNotFoundException {
			byte[] receiveData = new byte[64096];
			while (true) {
				receivePacket = new DatagramPacket(receiveData, receiveData.length);
				try {
					serverSocket.receive(receivePacket);
				} catch (IOException e) {
					e.printStackTrace();
				}

				Packet packet = new Packet();
				packet.seq_number = receiveData[0];
				packet.noMoreSegs = receiveData[1];
				acks.add((int) packet.seq_number);
				System.out.println();
				System.out.println("Received ACK: " + packet.seq_number);
				System.out.println();

				// If there is no congestion the, window size is incremented by
				// 1 each time it receives an acknowledgement. If congestion
				// occurs and if window size is less than the threshold value,
				// then too window sixe is incremented by 1 each time it
				// receives an acknowledgement.
				if (congestionFlag == false || (congestionFlag == true && window < threshold)) {
					window++;

					// In case of congestion, and window size is greater than
					// the threshold value then the window size is incremented
					// by 1 only after it gets all the acknowledgments of
					// packets sent at once. The 1/window effectively does the
					// same.
				} else if (congestionFlag = true && window > threshold) {
					window = window + 1 / window;
				}
				
				// Here, it is checked if any acknowledgments are missing. If
				// yes, congestion is assumed to happen, so the threshold value
				// is manipulated and the packet has to be resent so the method
				// resendPackets() is called
				double packetsLeftToArrive = window % acks.size();
				if (packetsLeftToArrive == 0) {
					Collections.sort(acks);
					end_time = System.currentTimeMillis();
					
					// Timeout period is set to 1000 since RTT is assumed to be
					// 500. If the acknowledgment is missing after the timeout,
					// then congestion is assumed to have occurred
					if (end_time - start_time > 1000) {
						for (int i = 1; i < acks.size(); i++) {
							if (acks.get(i) - acks.get(i - 1) == 1) {
								continue;
							} else {
								ClientSendPackets csp1 = new ClientSendPackets();
								csp1.resendPackets(acks.get(i));
								threshold = window / 2;
								window = 1;
								congestionFlag = true;
							}
						}
					}
				}
			}
		}
	}

	/*
	 * The main method of the parent class Buoy_Sender starts the client and
	 * server thread.
	 */
	public static void main(String[] args) {
		Buoy_Sender buoy_sender = new Buoy_Sender();

		Client client = buoy_sender.new Client();
		client.start();
		Server server = buoy_sender.new Server();
		server.start();
	}
}
