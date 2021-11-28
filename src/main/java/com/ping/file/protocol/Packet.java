
package com.ping.file.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * 数据传输包.
 * 
 * @author lawnstein.chan
 * @version $Revision:$
 */
public class Packet implements Serializable {
	public Command command;
	public boolean cmdResult;
	public String cmdMesg;

	public String lfilename;
	// public String rfilename;
	public String chksum;
	// public long filesize;
	public long chunkIndex;
	public byte[] chunkBytes;

	public Packet() {
		super();
		this.cmdResult = true;
		this.cmdMesg = null;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Packet [command=");
		builder.append(command);
		builder.append(", cmdResult=");
		builder.append(cmdResult);
		builder.append(", cmdMesg=");
		builder.append(cmdMesg);
		builder.append(", lfilename=");
		builder.append(lfilename);
		// builder.append(", rfilename=");
		// builder.append(rfilename);
		builder.append(", chksum=");
		builder.append(chksum);
		// builder.append(", filesize=");
		// builder.append(filesize);
		builder.append(", chunkIndex=");
		builder.append(chunkIndex);
		builder.append(", chunkBytes.length=");
		builder.append(chunkBytes == null ? 0 : chunkBytes.length);
		builder.append("]");
		return builder.toString();
	}

	public Packet clone() {
		Packet p = new Packet();
		p.cmdResult = true;
		p.cmdMesg = null;
		p.lfilename = this.lfilename;
		// p.rfilename = this.rfilename;
		p.chksum = null;
		// p.filesize = this.filesize;
		p.chunkIndex = this.chunkIndex;
		p.chunkBytes = null;
		return p;
	}

	public byte[] getBytes() {
		ObjectOutputStream out = null;
		try {
			ByteArrayOutputStream bout = new ByteArrayOutputStream();
			out = new ObjectOutputStream(bout);
			out.writeObject(this);
			out.flush();
			byte[] target = bout.toByteArray();
			return target;
		} catch (IOException e) {
			throw new RuntimeException("serialize Packet exception", e);
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
				}
			}
		}
	}

	public static Packet valueOf(byte[] bytes) {
		ObjectInputStream in = null;
		try {
			in = new ObjectInputStream(new ByteArrayInputStream(bytes));
			return (Packet) in.readObject();
		} catch (IOException | ClassNotFoundException e) {
			throw new RuntimeException("deserialize to Packet exception", e);
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
				}
			}
		}
	}
}
