
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

	public String filename;
	public String chksum;
	public Long filepos;
	public Long filesize;
	public Long chunkSize;
	public byte[] chunkBytes;

	public Packet() {
		super();
		this.cmdResult = true;
		this.cmdMesg = null;
		this.chunkSize = null;
		this.filepos = null;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Packet [command=");
		builder.append(command);
		builder.append(", cmdResult=");
		builder.append(cmdResult);
		if (cmdMesg != null) {
			builder.append(", cmdMesg=");
			builder.append(cmdMesg);
		}
		if (filename != null) {
			builder.append(", filename=");
			builder.append(filename);
		}
		if (chksum != null) {
			builder.append(", chksum=");
			builder.append(chksum);
		}
		if (filepos != null) {
			builder.append(", filepos=");
			builder.append(filepos);
		}
		if (filesize != null) {
			builder.append(", filesize=");
			builder.append(filesize);
		}
		if (chunkSize != null) {
			builder.append(", chunkSize=");
			builder.append(chunkSize);
		}
		if (chunkBytes != null) {
			builder.append(", chunkBytes.size=");
			builder.append(chunkBytes == null ? 0 : chunkBytes.length);
		}
		builder.append("]");
		return builder.toString();
	}

	public Packet clone() {
		Packet p = new Packet();
		p.cmdResult = true;
		p.cmdMesg = null;
		p.filename = this.filename;
		// p.rfilename = this.rfilename;
		p.chksum = null;
		// p.filesize = this.filesize;
		p.filepos = this.filepos;
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
