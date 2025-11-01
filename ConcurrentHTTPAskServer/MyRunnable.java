import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import tcpclient.TCPClient;


public class MyRunnable implements Runnable {
    String askServerHostname;
    String askServerString = null;
    String httpResponseMethod = "HTTP/1.1 400 Bad Request";
    String httpResponseBody = "";
    Integer askServerPort = null;
    Integer askServerTimeout = null;
    Integer askServerLimit = null;
    Boolean askServerShutdown = false;
    ServerSocket server;


    @Override
    public void run() {
        try {
            startClientThread();
        } catch (Exception e) {
            System.out.println(e.getLocalizedMessage());
        }
    }

    public MyRunnable(ServerSocket server) throws IOException {
        this.server = server;
    }
    
    /**
     * @param httpHeader 
     * @param port 
     * @throws Exception 
     */
    public void parseHTTPParameters(String httpHeader, int port) throws Exception {
        String methodLine = httpHeader.split("\n")[0];
        String httpMethod = methodLine.split(" ")[0].trim();
        String httpVers = methodLine.split(" ")[2].trim();
        
        
        if (!httpMethod.toUpperCase().equals("GET")) {
            throw new Exception("WrongHTTPMethod");
        }

        if (!httpVers.trim().equals("HTTP/1.1")) {
            throw new Exception("WrongHTTPVersion");
        }

        if (!httpHeader.toLowerCase().contains("host:")) {
            throw new Exception("HostMissing");
        }

        String spot = methodLine.split(" ")[1];
        String rawParamString = spot.split("\\?")[1];
        String[] rawParams = rawParamString.split("&");

        String reqPath = spot.split("\\?")[0].trim();

        if (!(reqPath.equals("/ask") || reqPath.equals("ask"))) {
            throw new Exception("Path not recognised. " + reqPath);
        }

        for (String s : rawParams) {
            String param = s.split("=")[0];
            String val = s.split("=")[1];

            if (param.equals("hostname")) {
                this.askServerHostname = val;
            } else if (param.equals("limit")) {
                this.askServerLimit = Integer.valueOf(val);
            } else if (param.equals("shutdown")) {
                this.askServerShutdown = Boolean.valueOf(val);
            } else if (param.equals("timeout")) {
                this.askServerTimeout = Integer.valueOf(val);
            } else if (param.equals("port")) {
                this.askServerPort = Integer.valueOf(val);
            } else if (param.equals("string")) {
                this.askServerString = val + "\n";
            }
        }
    }

    /**
     * @throws IOException
     */
    public void startClientThread() throws IOException {
        Socket socket = server.accept();

        try {
            new Thread(new MyRunnable(server)).start();

            InputStream dataInputStream = socket.getInputStream();
            int ch = dataInputStream.read();
            StringBuilder inString = new StringBuilder();
            while (true) {
                inString.append((char) ch);
                if (inString.toString().endsWith("\r\n\r\n"))
                    break;
                ch = dataInputStream.read();
            }

            socket.shutdownInput();

            parseHTTPParameters(inString.toString(), this.server.getLocalPort());

            TCPClient tcpClient = new TCPClient(askServerShutdown, askServerTimeout, askServerLimit);
            if (askServerString == null) {
                this.httpResponseBody = tcpClient.askServer(askServerHostname, askServerPort, "".getBytes());
            } else {
                this.httpResponseBody = tcpClient.askServer(askServerHostname, askServerPort,
                        askServerString.getBytes());
            }
            this.httpResponseMethod = "HTTP/1.1 200 OK";

        } catch (Exception e) {
            if (e instanceof SocketTimeoutException) {
                this.httpResponseMethod = "HTTP/1.1 408 Request Timeout";
            } else if (e instanceof UnknownHostException) {
                this.httpResponseMethod = "HTTP/1.1 404 Not Found";
            } else {
                this.httpResponseMethod = "HTTP/1.1 400 Bad Request";
            }
        }

        OutputStream dataOutputStream = socket.getOutputStream();
        PrintWriter writer = new PrintWriter(dataOutputStream, true);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O");
        String httpDate = formatter.format(ZonedDateTime.now(ZoneOffset.UTC));

        String httpResponseMessage = String.format(
                """
                        %s
                        Server:
                        Content-Type: text/plain; charset=utf-8;
                        Date: %s
                        Keep-Alive: timeout=5, max=1000
                        Connection: Keep-Alive

                        %s
                        """, this.httpResponseMethod, httpDate, this.httpResponseBody);

        writer.println(httpResponseMessage);
        socket.shutdownOutput();

    }
}
