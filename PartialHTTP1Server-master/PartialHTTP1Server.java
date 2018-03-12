/* Author: Joseph Gormley & Liam O'Brien - Date: 06/14/17 */
import java.nio.file.*;
import java.net.*;
import java.text.*;
import java.io.*;
import java.util.*;

public class PartialHTTP1Server extends Thread{
	
	static String[] firstLine = null;
	static ServerSocket serverSocket;
	static List<Thread> threadArray = Collections.synchronizedList(new ArrayList<Thread>(5));
	
	public static void main (String[] args) throws InterruptedException {

		// Error checks - arguements. 
		int port = Integer.parseInt(args[0]);		
		try {
			if (port < 1200 || port > 65535) 
				System.out.println("Error: port must be between 1200 - 65535\n");
				
		}
		catch(Exception e) {
			System.out.println("Error trying to connect to port\n");
		}
		

		try{
			serverSocket = new ServerSocket(port);				
		}catch(IOException e){ 
			System.out.println("Cannot create socket on port: " + port);
			return;
		}


		System.out.println("Awaiting connection...");

		// Server socket was created successfully, now wait... 	
		while(true) {
				
				try{
					// Accept incoming connections
					Socket server = serverSocket.accept();
					 
					// Connected to client, giving workload to thread
					if(threadArray.size() < 50){
						Thread newThread = new Thread(new ThreadHandler(server));
						threadArray.add(newThread);
						newThread.start();
					}else
						new PrintWriter(server.getOutputStream(), true).println("HTTP/1.0 503 Service Unavailable\r\n");
							
				}catch(IOException e){
					System.out.println("HTTP/1.0 500 Internal Server Error\r\n\r\n");
				}
			}
		}

	public static final class ThreadHandler extends Thread {

		Socket server;
		BufferedReader socketIn;	
		ArrayList<String> httpRequest = new ArrayList<String>(); // Proper formatted request
		PrintWriter response = null; // Server response to request
		
		// Constructor
		public ThreadHandler(Socket server){ this.server = server; }

		public void run() {


			String[] request = null;
			String input = null; // Client request
			
			try{
				server.setSoTimeout(3000);
				response = new PrintWriter(server.getOutputStream());
				BufferedReader br = new BufferedReader(new InputStreamReader(server.getInputStream()));
				httpRequest.add(br.readLine());
				while(br.ready()){
					input = br.readLine(); 
					httpRequest.add(input);
				}

				System.out.println("-------NEW REQUEST---------\n");
				for(String s : httpRequest)
					System.out.println(s);

				request = httpRequest.get(0).split(" ");
				if(request.length != 3 || !request[0].matches("[A-Z]+") || request[1].charAt(0) != '/' || !request[2].startsWith("HTTP/")){
					response.println("HTTP/1.0 400 Bad Request\r\n\r\n");
					response.flush();
					threadArray.remove(Thread.currentThread());
					return;
				}
				
			}catch(SocketTimeoutException e){
				response.println("HTTP/1.0 408 Request Timeout\r\n");
				response.flush();
				threadArray.remove(Thread.currentThread());
				return;
			}catch(Exception e){ 
				response.println("HTTP/1.0 500 Internal Server Error\r\n");
				response.flush();
				threadArray.remove(Thread.currentThread());
				return; 	
			}	
			
			//VERSION
			try{
				double version = Double.parseDouble(request[2].substring(5));
				if(version < 1.0){
					if(request[0].equals("GET"))
						get();
					else{
						response.println("HTTP/1.0 400 Bad Request");
						response.flush(); 
					}
				}else if(version == 1.0 || version == 1.1){
					if(request[0].equals("GET"))
						get();
					else if(request[0].equals("HEAD"))
						head();
					
					else if(request[0].equals("POST")){
						post();				
					}
					else if(request[0].equals("DELETE") || request[0].equals("PUT") || request[0].equals("LINK") || request[0].equals("UNLINK")){
						response.println("HTTP/1.0 501 Not Implemented\r\n\r\n"); 
						response.flush();
					}else{
						response.println("HTTP/1.0 400 Bad Request\r\n\r\n");
						response.flush();
					}
				}else{
					response.println("HTTP/1.0 505 HTTP Version Not Supported\r\n\r\n");
					response.flush();
				}
				threadArray.remove(Thread.currentThread());
				server.close();
				return;
				
			}catch(Exception e){  // Bad Version number
				e.printStackTrace(System.out);
			}	
		}
		
		public void get() throws Exception {

			String type = "application/octet-stream";
			String payload = "";
			String[] request = httpRequest.get(0).split(" ");
			File file = new File("." + request[1]);
			if(!file.exists()){
				response.println("HTTP/1.0 404 Not Found\r\n");
				response.flush();
				return; 
			}
			if(!file.canRead()){
				response.println("HTTP/1.0 403 Forbidden\r\n");
				response.flush();
				return;
			}
			
			// FILE EXISTS W/ READ PERMISSION, SO DETERMINE TYPE
			if(request[1].endsWith(".txt") || request[1].endsWith(".html"))
				type = "text/" + request[1].substring(request[1].indexOf('.') + 1);
			else if(request[1].endsWith(".gif") || request[1].endsWith(".jpeg")|| request[1].endsWith(".png"))
				type = "image/" + request[1].substring(request[1].indexOf('.') + 1);
			else if(request[1].endsWith(".pdf") || request[1].endsWith(".zip")|| request[1].endsWith(".x-gzip"))
				type = "application/" + request[1].substring(request[1].indexOf('.') + 1);
			
			
			
			
			// IF-MODIFIED-SINCE HEADER?
			SimpleDateFormat sdf = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
			sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
			try{
				if(httpRequest.size() == 3 && httpRequest.get(1).startsWith("If-Modified-Since: ")){
					if(!(sdf.parse(sdf.format(sdf.parse(httpRequest.get(1).substring(19))))).before(sdf.parse(sdf.format(new Date(file.lastModified()))))){			
						response.print("HTTP/1.0 304 Not Modified\r\n");
						response.print("Expires: Tue, 01 May 2018 08:00:00 GMT\r\n");
						response.flush();
						return;
					}
				}		
			}catch(ParseException e){
				httpRequest.remove(1);
				get();
			}
			
			// GIVE CLIENT STATUS CODE & HEADERS
			response.print("HTTP/1.0 200 OK\r\n");
			response.print("Content-Type: " + type + "\r\n");
			response.print("Content-Length: " + file.length() + "\r\n");
			response.print("Content-Encoding: identity" + "\r\n"); 
			response.print("Last-Modified: " + sdf.format(new Date(file.lastModified())) + "\r\n"); 
			response.print("Allow: GET, POST, HEAD\r\n");
			response.print("Expires: Tue, 01 May 2018 08:00:00 GMT\r\n");
			response.print("\r\n");
			response.flush();
			
			// GIVE CLIENT PAYLOAD FROM FILE
			if(!type.startsWith("image/") && !type.startsWith("application/")){
				Scanner sc = new Scanner(file);
				sc.useDelimiter("\\Z");
				response.print(sc.next() + "\r\n");
			}else{	
				byte[] image = new byte[(int)file.length()];
				server.getOutputStream().write(image, 0, new FileInputStream(file).read(image));
				response.print("\r\n");	   
			}
			response.flush();
			return;
		}
	
					
						
		public void head() throws Exception {
			String type = "application";
			String[] request = httpRequest.get(0).split(" ");
			File file = new File("." + request[1]);
			String payload = "";
			
			if(!file.exists()){
				response.println("404 Not Found");
				return;
			}if(!file.canRead()){
				response.println("403 Forbidden");
				return;
			}
			
			// FILE EXISTS W/ READ PERMISSION
			if(request[1].endsWith(".txt") || request[1].endsWith(".html"))
				type = "text/" + request[1].substring(request[1].indexOf('.') + 1);
			else if(request[1].endsWith(".gif") || request[1].endsWith(".jpeg")|| request[1].endsWith(".png"))
				type = "image/" + request[1].substring(request[1].indexOf('.') + 1);
			else if(request[1].endsWith(".pdf") || request[1].endsWith(".zip")|| request[1].endsWith(".x-gzip"))
				type = "application/" + request[1].substring(request[1].indexOf('.') + 1);
			
			
			SimpleDateFormat sdf = new SimpleDateFormat("E, dd MMM yyy HH:mm:ss z");
			sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
			
			// GIVE CLIENT HEADERS/PAYLOAD
			response.print("HTTP/1.0 200 OK\r\n");
			response.print("Content-Type: " + type + "\r\n");
			response.print("Content-Length: " + file.length() + "\r\n");
			response.print("Content-Encoding: identity" + "\r\n"); 
			response.print("Last-Modified: " + sdf.format(new Date(file.lastModified())) + "\r\n"); 
			response.print("Allow: GET, POST, HEAD\r\n");
			response.print("Expires: Tue, 01 May 2018 08:00:00 EST\r\n");
			response.print("\r\n");
			response.flush();
			return;
		}	
	
		public void post(){

			String arg = httpRequest.get(httpRequest.size() -1);			
			String payload = "";
			String type = null;
			String status = null;
			String from = null;
			String userAgent = null;
			String[] request = httpRequest.get(0).split(" ");
			File file = new File("." + request[1]);
			boolean typeExists = false;
			boolean lengthExists = false;		
			
			// .CGI SCRIPT?
			if(!request[1].endsWith(".cgi"))
				status = "HTTP/1.0 405 Method Not Allowed\r\n";

			if(status == null && !file.exists())
					status = "HTTP/1.0 404 Not Found\r\n";
			if(status == null && (!file.canRead() || !file.canExecute()))
					status = "HTTP/1.0 403 FForbidden\r\n";
                        
			// DO HEADERS EXIST? IF SO, DOES 'FROM' && 'USER-AGENT'?
			for(String s : httpRequest){
				if(status == null && s.startsWith("Content-Length: ")){
					lengthExists = true;
					try{
						Integer.parseInt(s.substring(16));
					}catch(Exception e){ // COULD GO OUT OF BOUNDS ON (16)
						lengthExists = false;
					}
				}
				if(status == null && s.equals("Content-Type: application/x-www-form-urlencoded"))
					typeExists = true;
						
				if(status == null && s.startsWith("From: "))
					from = s.substring(6);
			
				if(status == null && s.startsWith("User-Agent: "))
					userAgent = s.substring(12);
			}	

			if(status == null && !lengthExists)
				status = "HTTP/1.0 411 Length Required\r\n";
			if(status == null && !typeExists)
				status = "HTTP/1.0 500 Internal Server Error\r\n";
			if(arg.equals(""))
				status = "HTTP/1.0 204 No Content";		
			
			// FORMAT IS PROPER
			if(status == null){
                 
				try{              
				
					// DECODE ARG ACCORDING TO RFC-3986
                    arg = URLDecoder.decode(arg, "UTF-8");
					
					// SET UP PROCESS ENVIRONMENT
					ProcessBuilder pb = new ProcessBuilder('.' + request[1]);	
					Map<String, String> env = pb.environment();
					env.put("CONTENT_LENGTH", Integer.toString(arg.length()));
					env.put("SCRIPT_NAME", request[1]);
					env.put("SERVER_NAME", InetAddress.getLocalHost().getHostName());
					env.put("SERVER_PORT", Integer.toString(server.getLocalPort()));
					env.put("REQUEST_METHOD", request[0]);
					env.put("HTTP_COOKIE", "");					
					env.put("QUERY_STRING", "");

					// STORE OTHER ENVIROMENT VARIABLES (HEADER DEPENDENT)
					if(from != null)
						env.put("HTTP_FROM", from);
					if(userAgent != null)
						env.put("HTTP_USER_AGENT", userAgent);
	
					// START PROCESS, SET UP STREAMS
					Process proc = pb.start();
					PrintWriter out = new PrintWriter(proc.getOutputStream());
					BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()));

					// SEND ARGS TO PROCESS
					out.println(arg);	
					out.flush();
					
					int msg;
					while((msg = in.read()) != -1){
						payload = payload + (char)msg;
						System.out.println("hey");
					}				
					
				}catch(Exception e){
					System.out.println(e);
				}

				if(request[1].endsWith(".cgi"))
					type = "text/html";
				else if(request[1].endsWith(".txt") || request[1].endsWith(".html"))
					type = "text/" + request[1].substring(request[1].indexOf('.') + 1);
				else if(request[1].endsWith(".gif") || request[1].endsWith(".jpeg")|| request[1].endsWith(".png"))
					type = "image/" + request[1].substring(request[1].indexOf('.') + 1);
				else if(request[1].endsWith(".pdf") || request[1].endsWith(".zip")|| request[1].endsWith(".x-gzip"))
					type = "application/" + request[1].substring(request[1].indexOf('.') + 1);				
				

				System.out.println();		
				System.out.println("CGI OUTPUT: " + payload);
				System.out.println();

				
				// GIVE CLIENT HEADERS/PAYLOAD
				response.print("HTTP/1.0 200 OK\r\n");
				response.print("Content-Type: " + type + "\r\n");
				response.print("Content-Length: " + payload.length() + "\r\n");
				response.print("Content-Encoding: identity" + "\r\n");
				SimpleDateFormat sdf = new SimpleDateFormat("E, dd MMM yyy HH:mm:ss z");
				sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
				response.print("Last-Modified: " + sdf.format(new Date(file.lastModified())) + "\r\n");
				response.print("Allow: GET, POST, HEAD\r\n");
				response.print("Connection: close\r\n");
				response.print("Expires: Tue, 01 May 2018 08:00:00 EST\r\n");
				response.print("\r\n");
				response.print(payload + "\r\n");
				response.flush();
				return;
			}
			
			response.print(status);
			response.flush();
			return;	
		}
	}
}
