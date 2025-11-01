import  tcpclient.TCPClient;
import java.net.*;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.io.*;

public class HTTPAsk {
    String askServerHostname;
    Integer askServerPort = null;
    Integer askServerTimeout = null;
    Integer askServerLimit = null;


    String askServerString = null;

    String httpResponseMethod = "HTTP/1.1 200 OK";

    String httpResponseBody = "";
    boolean askServerShutdown = false;

    public void parseHTTPParameters(String httpHeader, int port) throws Exception {
        String methodLine = httpHeader.split("\n")[0];
        String httpMethod = methodLine.split(" ")[0].trim();
        String httpVersion = methodLine.split(" ")[2].trim();

        if (!httpMethod.toUpperCase().equals("GET")) {
            System.out.println("HTTPAsk expected a GET request but instead received " + httpMethod);
            throw new Exception("IncorrectHTTPMethodException");
        }

        if (!httpVersion.trim().equals("HTTP/1.1")) {
            System.out.println("Received incorrect HTTP version: " + httpVersion + " when expected was HTTP/1.1");
            throw new Exception("IncorrectHTTPVersionException");
        }

        if (!httpHeader.toLowerCase().contains("host:")) {
            System.out.println("Host was not provided");
            throw new Exception("HostMissingException");
        }

        for (String each: httpHeader.split("\n")) {
            if (each.toLowerCase().contains("host:")) {
                String httpRequestHost = each.subSequence(6, each.length()).toString().trim();
                System.out.println(httpRequestHost);
                if (!httpRequestHost.equals("localhost:" + String.valueOf(port))) {
                    System.out.println("Received incorrect hostname: " + httpRequestHost + " when expected hostname was localhost:" + port);
                    throw new Exception("HostnameMismatchException");
                }
            }
        }

        String location = methodLine.split(" ")[1];
        String requestPath = location.split("\\?")[0].trim();
        String rawParamString = location.split("\\?")[1];
        String[] rawParams = rawParamString.split("&");

        if (!(requestPath.equals("/ask") || requestPath.equals("ask"))) {
            System.out.println("Received path " + requestPath + " which is not recognised");
            throw new UnknownHostException();
        }

        for (String each : rawParams) {
            String param = each.split("=")[0];
            String value = each.split("=")[1];

            if (param.equals("hostname")) {
                this.askServerHostname = value;
            } else if (param.equals("limit")) {
                this.askServerLimit = Integer.valueOf(value);
            } else if (param.equals("shutdown")) {
                this.askServerShutdown = Boolean.valueOf(value);
            } else if (param.equals("timeout")) {
                this.askServerTimeout = Integer.valueOf(value);
            } else if (param.equals("port")) {
                this.askServerPort = Integer.valueOf(value);
            } else if (param.equals("string")) {
                this.askServerString = value + "\n";
            }
        }
    }

    public void startServer(int port) throws Exception {
        ServerSocket server = new ServerSocket(port);
        System.out.println("Service side message: Starting server");

        // thread blocked until a connection is made
        Socket socket = server.accept();

        System.out.println("Service side message: Received an HTTP request, printing header");

        // Get input stream
        InputStream in = socket.getInputStream();

        // Read data sent by the client
        int character = in.read();
        StringBuilder inString = new StringBuilder();

        while (true) {
            inString.append((char) character);
            if (inString.toString().endsWith("\r\n\r\n")) break;
            character = in.read();
        }

        socket.shutdownInput();

        try {
            parseHTTPParameters(inString.toString(), port);
            TCPClient tcpClient = new TCPClient(askServerShutdown, askServerTimeout, askServerLimit);

            if (askServerString == null) {
                this.httpResponseBody = tcpClient.askServer(askServerHostname, askServerPort, "".getBytes());
            } else {
                this.httpResponseBody = tcpClient.askServer(askServerHostname, askServerPort, askServerString.getBytes());
            }
            this.httpResponseMethod = "HTTP/1.1 200 OK";
        } catch (Exception e) {
            if (e instanceof SocketTimeoutException) {
                System.out.println("Encountered SocketTimeoutException in the try-catch block");
                this.httpResponseMethod = "HTTP/1.1 408 Request Timeout";
            } else if (e instanceof UnknownHostException) {
                System.out.println("Encountered UnknownHostException in the try-catch block");
                this.httpResponseMethod = "HTTP/1.1 404 Not Found";
            } else {
                System.out.println("Encountered in unexpected error in the try-catch block with message: " + e.getLocalizedMessage());
                this.httpResponseMethod = "HTTP/1.1 400 Bad Request";
            }
        }

        OutputStream out = socket.getOutputStream();
        PrintWriter writer = new PrintWriter(out, true);

        // HTTP appropriate date
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O");
        String httpDate = formatter.format(ZonedDateTime.now(ZoneOffset.UTC));

        String httpResponseMessage = String.format(
"""
%s
Server: Ginga Ginga Pinga Pinga
Content-Type: text/plain; charset=utf-8;
Date: %s
Keep-Alive: timeout=5, max=1000
Connection: Keep-Alive

%s
""", httpResponseMethod, httpDate, httpResponseBody);

        writer.println(httpResponseMessage);
        socket.shutdownOutput();
        server.close();
    }

    public static void main(String[] args) throws Exception {
        // extract port number from cli args
        int port = Integer.valueOf(args[0]);
        HTTPAsk server = new HTTPAsk();
        server.startServer(port);
    }
}

