
package com.ping;

import java.io.IOException;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.ping.configure.ClientProperties;
import com.ping.configure.ServProperties;
import com.ping.file.client.TcpClient;
import com.ping.file.serv.TcpServer;

/**
 * 网络通讯测试
 * 
 * @author lawnstein.chan
 * @version $Revision:$
 */
@SpringBootApplication
public class PingpApplication {
	protected final static Logger LOG = LoggerFactory.getLogger(PingpApplication.class);
	protected static ConfigurableApplicationContext applicationContext;

	public static void main(String[] args) {
		applicationContext = SpringApplication.run(PingpApplication.class, args);
		if (args.length < 1) {
			String ip = args.length >= 1 ? args[0] : "127.0.0.1";
			int port = args.length >= 2 ? Integer.valueOf(args[1]) : 80;
			ping(ip, port);
			
		} else if ("upload".equalsIgnoreCase(args[0]) || "up".equalsIgnoreCase(args[0])) {
			if (args.length >= 2 && "help".equals(args[1])) {
				System.out.println("up [ip [port [path [max-threads] ] ] ]");
				return;
			}

			String ip = args.length >= 2 ? args[1] : "127.0.0.1";
			int port = args.length >= 3 ? Integer.valueOf(args[2]) : 80;
			String path = args.length >= 4 ? args[3] : "./";
			int maxThreads = args.length >= 5 ? Integer.valueOf(args[4]) : 0;
			upload(ip, port, path, maxThreads);

		} else if ("download".equalsIgnoreCase(args[0]) || "dw".equalsIgnoreCase(args[0])) {
			if (args.length >= 2 && "help".equals(args[1])) {
				System.out.println("dw [ip [port [path [max-threads] ] ] ]");
				return;
			}

			String ip = args.length >= 2 ? args[1] : "127.0.0.1";
			int port = args.length >= 3 ? Integer.valueOf(args[2]) : 80;
			String path = args.length >= 4 ? args[3] : "./";
			int maxThreads = args.length >= 5 ? Integer.valueOf(args[4]) : 0;
			download(ip, port, path, maxThreads);
			
		} else if ("server".equalsIgnoreCase(args[0])) {
			if (args.length >= 2 && "help".equals(args[1])) {
				System.out.println("server [port [dir [max-threads] ] ]");
				return;
			}

			int port = args.length >= 2 ? Integer.valueOf(args[1]) : 0;
			String dir = args.length >= 3 ? args[2] : null;
			int maxThreads = args.length >= 4 ? Integer.valueOf(args[3]) : 0;
			server(port, dir, maxThreads);
			
		} else {
			String ip = args.length >= 1 ? args[0] : "127.0.0.1";
			int port = args.length >= 2 ? Integer.valueOf(args[1]) : 80;
			ping(ip, port);			
		}
	

	}

	public static void ping(String ip, int port) {
		Socket client = null;
		try {
			LOG.info("connect to " + ip + ":" + port + " ......");
			client = new Socket(ip, port);
			if (client.isConnected()) {
				LOG.info("connect to " + ip + ":" + port + " success.");
			} else {
				LOG.info("connect to " + ip + ":" + port + " uncompleted ...");
			}
		} catch (IOException e) {
			LOG.error("connect to " + ip + ":" + port + " " + e.getClass().getSimpleName() + ", " + e.getMessage());
		} finally {
			if (client != null) {
				try {
					client.close();
				} catch (IOException e) {
				}
			}
		}
	}

	public static void upload(String ip, int port, String path, int maxThreads) {
		ClientProperties prop = applicationContext.getBean(ClientProperties.class);
		TcpClient client = new TcpClient(ip, port, path, maxThreads, prop);
		client.upload();
	}

	public static void download(String ip, int port, String path, int maxThreads) {
		ClientProperties prop = applicationContext.getBean(ClientProperties.class);
		TcpClient client = new TcpClient(ip, port, path, maxThreads, prop);
		client.download();
	}

	public static void server(int port, String dir, int maxThreads) {
		ServProperties prop = applicationContext.getBean(ServProperties.class);
		LOG.info("starting server on {} ...", port > 0 ? port : prop.port);
		TcpServer serv = new TcpServer(port, dir, maxThreads, prop);
		serv.start();
	}

}
