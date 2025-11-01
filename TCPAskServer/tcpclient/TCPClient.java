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

    public byte[] askServer(String hostname, int port, byte[] toServerBytes) throws IOException {
        ByteArrayOutputStream receivedData = new ByteArrayOutputStream();
        Socket socket = new Socket(hostname, port);

        if (timeout != null) {
            socket.setSoTimeout(timeout);
        }

        try {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            if (toServerBytes != null && toServerBytes.length > 0) {
                out.write(toServerBytes);
                out.flush();
            }

            if (shutdown) {
                socket.shutdownOutput();
            }

            byte[] buffer = new byte[1024];
            int totalBytesRead = 0;
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                int bytesToStore = bytesRead;

                if (limit != null && totalBytesRead + bytesRead > limit) {
                    bytesToStore = limit - totalBytesRead;
                }

                receivedData.write(buffer, 0, bytesToStore);
                totalBytesRead += bytesToStore;

                if (limit != null && totalBytesRead >= limit) {
                    break;
                }
            }
        } catch (SocketTimeoutException e) {
        } finally {
            socket.close();
        }

        return receivedData.toByteArray();
    }
}
