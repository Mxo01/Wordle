// I/O imports
import java.io.IOException;

// Connection imports
import java.net.DatagramPacket;
import java.net.MulticastSocket;

// Utility imports
import java.util.ArrayList;
import java.util.List;

public class MulticastHandler implements Runnable {
  private List<String> notifications;
  private MulticastSocket client;

  public MulticastHandler(MulticastSocket client) {
    this.notifications = new ArrayList<String>();
    this.client = client;
  }

  // Wait for notifications from the server
  public void run() {
    while (true) {
      DatagramPacket notificationBytes = new DatagramPacket(new byte[1024], 1024); // create a new request datagram packet

      try {this.client.receive(notificationBytes);} // receive the notification from the multicast group and store into the buffer
      catch (IOException e) {break;}

      String notification = new String(notificationBytes.getData()); // get the notification as a string
      notifications.add(notification); // add the notification into the list of notifications
    } 
  }

  // Print all the notifications
  public void showNotifications() {
    System.out.println("");

    for (String notify: this.notifications) {
      System.out.println(notify + "\n");
    }
  }
}