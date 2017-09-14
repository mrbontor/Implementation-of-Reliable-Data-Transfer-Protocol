
/**
 * 
 * filename: Packet.java
 * 
 * version: 1.0 05/01/2017
 *
 * revisions: Initial version
 * 
 * @author Parvathi Nair pan7447
 * 
 *        
 */

/*
 * This is the packet class. The header has two fields seq_number and noMoreSegs
 * noMoreSegs indicates if the packet is the last packet.The data is the payload
 * (image in byte format) which is 64kb as 64kb is the maximum that we can send
 * in UDP
 * 
 * The header size is 2 bytes and so the total packet size is 64002bytes
 */

public class Packet {

	byte seq_number;
	byte noMoreSegs;
	byte[] data;

	public Packet() {
		seq_number = 0;
		noMoreSegs = 0;
		data = new byte[64000];
	}

}
