// I/O imports
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

// Connection imports
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.Socket;

// Utility imports
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public class WordleClientMain {
  private static final String ANSI_RESET = "\u001B[0m"; // color reset
  private static final String ANSI_YELLOW = "\u001B[33m"; // yellow
  private static final String ANSI_GREEN = "\u001B[32m"; // greeen
  public static void main(String args[]) {
    try {
      BufferedReader configReader = new BufferedReader(new InputStreamReader(new FileInputStream("ClientConfig.txt"))); // client configuration file reader

      String hostname = configReader.readLine(); // read the hostname from the configuration file
      int port = Integer.parseInt(configReader.readLine()); // read the port from the configuration file
      String multicastHostname = configReader.readLine(); // read the multicast address from the configuration file

      InetAddress multicastAddr = InetAddress.getByName(multicastHostname); // multicast group address
      InetSocketAddress multicastGroup = new InetSocketAddress(multicastAddr, port); // multicast group 
      NetworkInterface netIF = NetworkInterface.getByName("bge0"); // network interface
    
      MulticastSocket multicastClient = new MulticastSocket(port); // create a multicast client socket

      MulticastHandler multicastHandler = new MulticastHandler(multicastClient); // create a multicast notification handler
      Thread multicastHandlerThread = new Thread(multicastHandler); // create a multicast notification handler thread

      Socket client = new Socket(hostname, port); // create a new client socket
      BufferedReader sockIn = new BufferedReader(new InputStreamReader(client.getInputStream())); // create a new BufferedReader to read from the socket
      PrintWriter sockOut = new PrintWriter(client.getOutputStream(), true); // create a new PrintWriter to write on the socket output
      
      Scanner sc = new Scanner(System.in); // scanner for command line 

      int tries = 0; // number of tries to guess the secret word

      // Initialize some boolean variables
      boolean registered = false;
      boolean loggedIN = false;
      boolean played = false;
      boolean changedWord = false;
      boolean loose = false;

      User currentUser = null; // current user

      while (true) {
        System.out.println("What do you want to do?");
        System.out.println("(1) Register");
        System.out.println("(2) Login");
        System.out.println("(3) Logout");
        System.out.println("(4) Play WORDLE");
        System.out.println("(5) Statistics");
        System.out.println("(6) Share");
        System.out.println("(7) Show me sharing");
        
        try {
          int action = Integer.parseInt(sc.nextLine()); // read the user's action

          // If action not in range 1-8
          if (action < 1 || action > 7) {
            System.out.println("Your action number must be in 1-7 range... Try again!\n");
            continue;
          }
  
          // Register
          if (action == 1) {
            if (registered) {
              System.out.println("You're already registered!\n"); 
              continue;
            }
  
            System.out.println("Please, register:");
    
            System.out.print("Username: ");
            String username = sc.nextLine(); // read the username from command line
    
            System.out.print("Password: ");
            String password = sc.nextLine(); // read the password from command line
            
            int result = register(username, password, client, sockIn, sockOut); // get the result of the registration
  
            if (result == -1) System.out.println("ERROR: password mustn't be empty!\n");
  
            if (result == 0) System.out.println("ERROR: this username is already in use! Try logging in...\n");
  
            if (result == 1) {
              System.out.println("Registration successful!\n");
              registered = true; // set registered to true
              action = 2; // set the action = 2 to log in the user
            }
          }
  
          // Login
          if (action == 2) {
            if (loggedIN) {
              System.out.println("You're already logged in!\n"); 
              continue;
            }
  
            System.out.println("Please, login:");
    
            System.out.print("Username: ");
            String username = sc.nextLine(); // read the username from command line
    
            System.out.print("Password: ");
            String password = sc.nextLine(); // read the password from command line
    
            int result = login(username, password, client, sockIn, sockOut); // read the result of the log in
  
            if (result == -1) System.out.println("ERROR: incorrect username or password!\n");
  
            if (result == 0) System.out.println("ERROR: you are already logged in...\n");
  
            if (result == 1) {
              System.out.println("Login successful!\n");
              loggedIN = true; // set loggedIN to true
              registered = true; // set registered to true

              currentUser = new User(username, password, new ArrayList<>(), new ConcurrentHashMap<Integer, Integer>(), new ArrayList<>(), new ArrayList<>()); // initialize a new current user

              multicastHandlerThread.start(); // start multicast notification thread
              multicastClient.joinGroup(multicastGroup, netIF); // join multicast group
            }
          }
  
          // Logout
          if (action == 3) {
            if (!loggedIN) {
              System.out.println("You must be logged in!\n"); 
              continue;
            }
  
            System.out.println("Are you sure you want to logout? Press enter to logout...");
            String answer = sc.nextLine().toLowerCase(); // read the answer from the command line
            
            // If the user pressed enter log out him
            if (answer.trim().isEmpty() || answer == null) { 
              int result = logout(currentUser.getUsername(), currentUser.getPassword(), client, sockIn, sockOut); // read the result of the log out
  
              if (result == -1) System.out.println("ERROR: there is no user logged in with this username and password, or you're already logged out!\n");
  
              if (result == 1) {
                System.out.println("Logout successful!\n");

                multicastClient.leaveGroup(multicastGroup, netIF); // leave the multicast group
                multicastHandlerThread.interrupt(); // interrupt the multicast notification handler thread

                break;
              }
            }        
          }
  
          // Play WORLDE
          if (action == 4) {
            if (!loggedIN) {
              System.out.println("You must be logged in!\n"); 
              continue;
            }
  
            int result = playWordle(currentUser.getUsername(), currentUser.getPassword(), client, sockIn, sockOut); // read the result of the check for play
  
            if (result == -1) System.out.println("ERROR: you've already played for this word! Please wait...\n");
  
            if (result == 1) {
              System.out.println("\nWELCOME TO WORDLE!");

              sockOut.println("5"); // send the request type to the server
  
              // Until it runs out of tries
              while (tries < 12) {
                changedWord = false; // the word has not changed
                loose = false; // if the user loose
                played = false; // the user is playing
                
                System.out.println("\nRemaining tries: " + (12-tries) + "\n");
  
                System.out.println("Guess the secret word:");
                String guessedWord = sc.nextLine(); // read the guessed word from input
                
                if (guessedWord.length() != 10) {
                  System.out.println("ERROR: You must enter only 10 character words!\n");
                  continue;
                }
                
                int response = sendWord(client, sockIn, sockOut, guessedWord); // read the result of sending word
                
                // If the word has changed
                if (response == 0) {
                  System.out.println("Ops... the word has changed!\n");
                  tries = 0; // reset tries
                  changedWord = true; // the word has changed
                  break;
                }
  
                // If the word is not in the words list
                if (response == -1) System.out.println("ERROR: this word is not in words list!\n");
                
                if (response == 1) {
                  String hintWord = sockIn.readLine(); // read the hint word from server
    
                  colorWord(guessedWord, hintWord); // color the letters
                  
                  // If the user wins
                  if (hintWord.equals("!!!!!!!!!!")) {
                    tries = 0; // reset tries
                    break;
                  }
  
                  tries++;
                }
              } 

              // If the user loose
              if (tries == 12) {
                System.out.println("\nSorry, you loose...\n");
                tries = 0; // reset tries
                loose = true; // set loose to true
                played = true; // set played to true
              }
    
              if (!loose && !changedWord) {
                System.out.println("\nCONGRATULATIONS!\n");
                tries = 0; // reset tries
                played = true; // set played to true
              }
            } 
          }
  
          // Send statistics
          if (action == 5) {  
            if (!loggedIN) {
              System.out.println("You must be logged in!\n"); 
              continue;
            }
            
            // Get statistics list from the server
            List<Integer> statistics = sendMeStatistics(currentUser.getUsername(), currentUser.getPassword(), client, sockIn, sockOut);
            int games = statistics.get(0);
            int wins = statistics.get(1);
            int currentStreak = statistics.get(2);
            int maxStreak = statistics.get(3);
  
            System.out.println("\nSTATISTICS\n");
            System.out.println("Played: " + games + "   Wins: " + (wins == 0 ? 0 : Math.round(wins * 100 / games)) + "%" + "   Current Streak: " + currentStreak + "   Max Streak: " + maxStreak + "\n");

            System.out.println("GUESS DISTRIBUTION\n");
            for (int i=4; i<16; i++) System.out.println("Attempt " + (i-3) + ": " + statistics.get(i));

            System.out.println();
          }
          
          // Share
          if (action == 6) {          
            if (!played) {
              System.out.println("You must have played to share the results!\n"); 
              continue;
            }

            share(sockOut); // share the result of the game on the multicast group
          }
          
          // Show sharing
          if (action == 7) {          
            if (!loggedIN) {
              System.out.println("You must be logged in!\n"); 
              continue;
            }
            
            multicastHandler.showNotifications(); // Print all the notification received
          }
        }
        catch (NumberFormatException e) {
          System.out.println("Your action must be a number... Try again!\n");
          continue;
        }
      }

      sc.close(); // close the System.in scanner
      configReader.close(); // close the configReader scanner
      sockIn.close(); // close the socket input stream
      sockOut.close(); // close the socket output stream
      multicastClient.close(); // close the multicast socket
      client.close(); // close the client
    }
    catch (Exception e) {e.printStackTrace();}
  }

  // Registration request
  private static int register(String username, String password, Socket client, BufferedReader sockIn, PrintWriter sockOut) throws IOException {
    sockOut.println("1"); // send the request type to the server
    sockOut.println(username); // send username 
    sockOut.println(password); // send password 

    int result = Integer.parseInt(sockIn.readLine()); // read the result of the registration from the server

    return result;
  }

  // Login request
  private static int login(String username, String password, Socket client, BufferedReader sockIn, PrintWriter sockOut) throws IOException {
    sockOut.println("2"); // send the request type to the server
    sockOut.println(username); // send username 
    sockOut.println(password); // send password 

    int result = Integer.parseInt(sockIn.readLine()); // read the result of the login from the server

    return result;
  }

  // Logout request
  private static int logout(String username, String password, Socket client, BufferedReader sockIn, PrintWriter sockOut) throws IOException {
    sockOut.println("3"); // send the request type to the server
    sockOut.println(username); // send username 
    sockOut.println(password); // send password 

    int result = Integer.parseInt(sockIn.readLine()); // read the result of the logout from the server

    return result;
  }

  // Play request
  private static int playWordle(String username, String password, Socket client, BufferedReader sockIn, PrintWriter sockOut) throws IOException {
    sockOut.println("4"); // send the request type to the server
    sockOut.println(username); // send username 
    sockOut.println(password); // send password 

    int result = Integer.parseInt(sockIn.readLine()); // read the result of the play request from the server

    return result;
  }

  // Send word request
  private static int sendWord(Socket client, BufferedReader sockIn, PrintWriter sockOut, String guessedWord) throws IOException {
    sockOut.println(guessedWord); // send guessed word

    int result = Integer.parseInt(sockIn.readLine()); // read the result of the guess from the server

    return result;
  }

  // Get statistics request
  private static List<Integer> sendMeStatistics(String username, String password, Socket client, BufferedReader sockIn, PrintWriter sockOut) throws IOException {
    sockOut.println("6"); // send the request type to the server
    sockOut.println(username); // send username 
    sockOut.println(password); // send password 

    int games = Integer.parseInt(sockIn.readLine()); // read the number of games from the server
    int wins = Integer.parseInt(sockIn.readLine()); // read the number of wins from the server
    int currentStreak = Integer.parseInt(sockIn.readLine()); // read the current streak from the server
    int maxStreak = Integer.parseInt(sockIn.readLine()); // read the max streak from the server

    // Read guess distribution
    int tries1 = Integer.parseInt(sockIn.readLine());
    int tries2 = Integer.parseInt(sockIn.readLine());
    int tries3 = Integer.parseInt(sockIn.readLine());
    int tries4 = Integer.parseInt(sockIn.readLine());
    int tries5 = Integer.parseInt(sockIn.readLine());
    int tries6 = Integer.parseInt(sockIn.readLine());
    int tries7 = Integer.parseInt(sockIn.readLine());
    int tries8 = Integer.parseInt(sockIn.readLine());
    int tries9 = Integer.parseInt(sockIn.readLine());
    int tries10 = Integer.parseInt(sockIn.readLine());
    int tries11 = Integer.parseInt(sockIn.readLine());
    int tries12 = Integer.parseInt(sockIn.readLine());
    
    // Store statistics into new list
    List<Integer> statistics = new ArrayList<>();
    statistics.add(games);
    statistics.add(wins);
    statistics.add(currentStreak);
    statistics.add(maxStreak);
    statistics.add(tries1);
    statistics.add(tries2);
    statistics.add(tries3);
    statistics.add(tries4);
    statistics.add(tries5);
    statistics.add(tries6);
    statistics.add(tries7);
    statistics.add(tries8);
    statistics.add(tries9);
    statistics.add(tries10);
    statistics.add(tries11);
    statistics.add(tries12);
    
    return statistics;
  }

  // Share request
  private static void share(PrintWriter sockOut) throws IOException {
    sockOut.println("7"); // send the request type to the server
  }

  // Print colored letters 
  private static void colorWord(String guessedWord, String hintWord) {
    for (int i=0; i<hintWord.length(); i++) {
      if (hintWord.charAt(i) == '!') System.out.print(ANSI_GREEN + guessedWord.charAt(i) + ANSI_RESET); // if the character is "!" print the guessedWord character green
      if (hintWord.charAt(i) == '?') System.out.print(ANSI_YELLOW + guessedWord.charAt(i) + ANSI_RESET); // if the character is "?" print the guessedWord character yellow
      if (hintWord.charAt(i) == '-') System.out.print(guessedWord.charAt(i)); // if the character is "-" print the guessedWord character as it was
    }
  }
}