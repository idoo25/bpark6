package server;

import javafx.application.Application;
import javafx.stage.Stage;
import serverGUI.ServerPortFrame;

/**
 * ServerUI is the main entry point for the ParkB server application.
 * It launches the JavaFX GUI for server configuration and monitoring.
 */
public class ServerUI extends Application {
    final public static int DEFAULT_PORT = 5555;

    public static void main(String args[]) throws Exception {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        ServerPortFrame aFrame = new ServerPortFrame();
        aFrame.start(primaryStage);
    }

    /**
     * Starts the parking server with the specified port
     * @param p The port number as a string
     */
    public static void runServer(String p) {
        int port = 0;

        try {
            port = Integer.parseInt(p);
        } catch (Throwable t) {
            System.out.println("ERROR - Could not parse port number!");
        }

        ParkingServer sv = new ParkingServer(port);

        try {
            sv.listen();
        } catch (Exception ex) {
            ServerPortFrame.str = "error";
            System.out.println("ERROR - Could not listen for clients!");
        }
    }
}