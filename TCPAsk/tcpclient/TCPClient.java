package tcpclient;
import java.net.*;
import java.io.*;

public class TCPClient {
    private boolean shutdown;
    private Integer timeout;
    private Integer limit;

    public TCPClient(boolean shutdown, Integer timeout, Integer limit) {
        this.shutdown = shutdown;
        this.timeout = timeout;
        this.limit = limit;
    }

    public String askServer(String hostname, int port, byte[] toServerBytes) throws Exception {
        Socket socket = new Socket(hostname, port);
        OutputStream outputStream = socket.getOutputStream();
        InputStream inputStream = socket.getInputStream();

        outputStream.write(toServerBytes);
        outputStream.flush();

        if (this.shutdown) {
            socket.shutdownOutput();
        }

        ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;

        if (this.timeout != null) {
            socket.setSoTimeout(timeout);
        }
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            responseStream.write(buffer, 0, bytesRead);
            if (limit != null && responseStream.size() >= limit) {
                return responseStream.toString();
            }
        }

        return responseStream.toString();
    }

}