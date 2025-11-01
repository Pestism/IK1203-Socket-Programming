import java.io.IOException;
import java.net.ServerSocket;

public class ConcHTTPAsk {
    public static void main(String[] args) throws IOException {
        System.out.println("Starting Server");
        ServerSocket server = new ServerSocket(Integer.valueOf(args[0]));

        new Thread(new MyRunnable(server)).start();
    }
}
