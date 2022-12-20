// I/O imports
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

// Connection imports
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.DatagramPacket;

// Utility imports
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// GSON imports
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class WordleServerMain {
  private static final File registeredUsersJSON = new File("RegisteredUsers.json"); // file JSON conteining registered users
  private static List<User> registeredUsersLIST = new ArrayList<>(); // list of registered users

  private static List<User> loggedUsersLIST = new ArrayList<>(); // list of logged users

  private static String secretWord = null; // secret word
  private static int secretWordNumber = getSecretWordNumber(); // secret word number

  public static void main(String[] args) throws IOException {
    BufferedReader configReader = new BufferedReader(new InputStreamReader(new FileInputStream("ServerConfig.txt"))); // server configuration file reader

    int port = Integer.parseInt(configReader.readLine()); // read the port from the configuration file
    String multicastHostname = configReader.readLine(); // read the multicast address from the configuration file
    int time = Integer.parseInt(configReader.readLine()); // read the timer from the configuration file

    List<String> words = Files.readAllLines(Paths.get("words.txt")); // get a list of words from words.txt
    
    secretWord = words.get(new Random().nextInt(words.size())); // get a random secret word from the words.txt file
    ++secretWordNumber;
    
    Timer timer = new Timer(); // create a new timer

    // Choose a random word every 5 minutes (300000 ms)
    timer.scheduleAtFixedRate(new TimerTask() {
      public void run() {
        secretWord = words.get(new Random().nextInt(words.size())); // get a random secret word from the words.txt file
        ++secretWordNumber;
      }
    }, time, time);

    InetAddress multicastAddr = InetAddress.getByName(multicastHostname); // multicast group address
    InetSocketAddress multicastGroup = new InetSocketAddress(multicastAddr, port); // multicast group 
    NetworkInterface netIF = NetworkInterface.getByName("bge0"); // network interface

    try (MulticastSocket multicastServer = new MulticastSocket(port)) { // try to create a multicast server socket
      try (ServerSocket server = new ServerSocket(port)) { // try to create a server socket and connect to it
        try (ExecutorService pool = Executors.newCachedThreadPool()) { // try to create a thread pool 
          multicastServer.setReuseAddress(true); // set reuse address for multicast server
          multicastServer.joinGroup(multicastGroup, netIF); // join the multicast group

          while (true) {
            try {pool.execute(new Accepter(server.accept(), multicastServer, words, multicastAddr, port));} // create a new Accepter that wait until client's connection   
            catch (IOException e) {break;} 
          } 

          timer.cancel(); // delete the timer
          configReader.close(); // close the scanner
          pool.shutdown(); // shut down the thread pool
          multicastServer.leaveGroup(multicastGroup, netIF); // leave the multicast group
        }
      }
    }
    catch (IOException e) {e.printStackTrace();}
  }

  private static class Accepter implements Runnable {
    private Socket client;
    private MulticastSocket multicastServer;
    private List<String> words;
    private InetAddress multicastAddr;
    private int port;
    
    public Accepter(Socket client, MulticastSocket multicastServer, List<String> words, InetAddress multicastAddr, int port) {
      this.client = client; 
      this.multicastServer = multicastServer;
      this.words = words;
      this.multicastAddr = multicastAddr;
      this.port = port;
    }

    public void run() {
      User currentUser = null; // current user

      boolean loggedIN = false; // if the current user is logged in

      try {
        BufferedReader sockIn = new BufferedReader(new InputStreamReader(this.client.getInputStream())); // try to create a new BufferedReader to read from the socket
        PrintWriter sockOut = new PrintWriter(this.client.getOutputStream(), true); // try to create a new PrintWriter to write on the socket output

        // Initialize statistics variables
        Integer games = 0, wins = 0, currentStreak = 0, maxStreak = 0;
        String numberOfTries = null; // number of tries for specific secret word and user

        while (true) {
          String requestType = sockIn.readLine(); // read request type from the client

          if (requestType != null) { // Ignore null reading from the socket
            // If user want to regiser
            if (requestType.equals("1")) {
              String username = sockIn.readLine(); // read the username from the client
              String password = sockIn.readLine(); // read the password from the client

              if (password.trim().isEmpty() || password == null) {
                sockOut.println("-1"); // if the password is empty
                continue;
              }

              int checkUser = checkUser(username, password); // check if the user is already registered
  
              if (checkUser == 1) sockOut.println("1"); // if the user isn't already registered 
  
              if (checkUser == 0) sockOut.println("0"); // if the user is already registered
            }
            
            // If user want to login
            if (requestType.equals("2")) {
              String username = sockIn.readLine(); // read the username from the client
              String password = sockIn.readLine(); // read the password from the client

              int login = logger(username, password); // check for successful login

              if (login == 1) {
                sockOut.println("1"); // if successful login
                
                currentUser = getCurrentUser(username, password); // get the current user

                loggedIN = true;
              }
  
              if (login == 0) sockOut.println("0"); // if the user is already logged in

              if (login == -1) sockOut.println("-1"); // if the password is incorrect
            }
            
            // If user want to logout
            if (requestType.equals("3")) {
              String username = sockIn.readLine(); // read the username from the client
              String password = sockIn.readLine(); // read the password from the client

              int logout = outLogger(username, password); // check for successful logout

              if (logout == 1) {
                sockOut.println("1"); // if successful logout
                break;
              }
  
              if (logout == -1) sockOut.println("-1"); // if there is no user logged in with this username and password or the user is already logged out
            }
            
            // If user want to play Wordle
            if (requestType.equals("4")) {
              String username = sockIn.readLine(); // read the username from the client
              String password = sockIn.readLine(); // read the password from the client

              int play = checkPlay(username, password); // check if the user is already playing

              if (play == 1) {
                sockOut.println("1"); // if the user never played for this word

                currentUser = getCurrentUser(username, password); // get the current user
              }

              if (play == -1) sockOut.println("-1"); // if the user played for this word
            }
            
            // If user want to send a word
            if (requestType.equals("5")) {
              List<String> playedWords = currentUser.getPlayedWords(); // get the current user's played words
              int tries = 0;
              boolean loose = false;

              while (tries < 12) {
                // If the  secret word has changed
                if ((playedWords.size() != 0) && (playedWords.get(playedWords.size() - 1) != secretWord)) {
                  sockOut.println("0"); // if the secret word has changed send "0"
                  tries = -1; // set tries to "-1" to as a special character to identify that the word has changed

                  playedWords.remove(playedWords.size() - 1); // remove the last played word
                  currentUser.setPlayedWords(playedWords); // set the new played words list to the logged in user
                  updateJSON(currentUser); // update the JSON file

                  break;
                }
                

                String guessedWord = sockIn.readLine(); // read the guessed word from the client
                
                // If the  secret word has changed
                if ((playedWords.size() != 0) && (playedWords.get(playedWords.size() - 1) != secretWord)) {
                  sockOut.println("0"); // if the secret word has changed send "0"
                  tries = -1; // set tries to "-1" to as a special character to identify that the word has changed

                  playedWords.remove(playedWords.size() - 1); // remove the last played word
                  currentUser.setPlayedWords(playedWords); // set the new played words list to the logged in user
                  updateJSON(currentUser); // update the JSON file
                  
                  break;
                }

                if (!this.words.contains(guessedWord)) {
                  sockOut.println("-1"); // if the guessed word is not in the word list
                  continue;
                }

                if (this.words.contains(guessedWord)) sockOut.println("1"); // if the guessed word is in the word list
                
                String hintWord = getHints(guessedWord); // get hints for the guessed word
                
                sockOut.println(hintWord); // send the guessed word with hint

                if (hintWord.equals("!!!!!!!!!!")) break; // if the user guess the secret word

                tries++;
              }

              // If the user loose
              if (tries == 12) {
                numberOfTries = "X";

                List<Integer> statistics = currentUser.getStatistics(); // get current user's statistics

                // Update statistics
                Integer  oldGames = statistics.get(0);
                games = ++oldGames;
                Integer oldWins = statistics.get(1);
                wins = oldWins;
                currentStreak = 0;
                Integer oldMaxStreak = statistics.get(3);
                maxStreak = oldMaxStreak;

                // Set new statistics to the current user
                currentUser.setStatistics(games, wins, currentStreak, maxStreak);
                updateJSON(currentUser); // update JSON file

                loose = true; // if the user loose
              }

              // If user guess the secret word
              if (!loose && tries != -1) {
                numberOfTries = ("" + ++tries + "").trim();
                List<Integer> statistics = currentUser.getStatistics(); // get current user's statistics

                // Update statistics
                Integer  oldGames = statistics.get(0);
                games = ++oldGames;
                Integer oldWins = statistics.get(1);
                wins = ++oldWins;
                Integer oldCurrentStreak = statistics.get(2);
                currentStreak = ++oldCurrentStreak;
                Integer oldMaxStreak = statistics.get(3);
                maxStreak = ++oldMaxStreak;

                ConcurrentHashMap<Integer, Integer> guessDistribution = currentUser.getGuessDistribution(); // get current user's guess distribution

                // Update guess distribution
                int oldVal = guessDistribution.get(tries); // get old value of this character from the HashMap
                guessDistribution.replace(tries, ++oldVal); // then replace it with incremented value

                // Set new statistics to the current user
                currentUser.setStatistics(games, wins, currentStreak, maxStreak);
                currentUser.setGuessDistribution(guessDistribution);
                updateJSON(currentUser); // update JSON file
              }
            }
            
            // If user wants his statistics 
            if (requestType.equals("6")) {
              String username = sockIn.readLine(); // read the username from the client
              String password = sockIn.readLine(); // read the password from the client

              // Get statistics list
              List<Integer> statistics = getStatistics(username, password);
              games = statistics.get(0);
              wins = statistics.get(1);
              currentStreak = statistics.get(2);
              maxStreak = statistics.get(3);

              // Set new statistics to the current user
              currentUser.setStatistics(games, wins, currentStreak, maxStreak);

              // Send statistics to the client
              sockOut.println(games); 
              sockOut.println(wins);
              sockOut.println(currentStreak);
              sockOut.println(maxStreak);

              ConcurrentHashMap<Integer, Integer> guessDistribution = currentUser.getGuessDistribution(); // get the guess distribution of this user

              for (int i=1; i<13; i++) sockOut.println(guessDistribution.get(i)); // send guess distribution to the user
            }
            
            // If user want to share his game's results
            if (requestType.equals("7")) {
              String sharingMessage = currentUser.getUsername() + ": Wordle " + secretWordNumber + " " + numberOfTries + "/12"; // result of the game (UDP message)

              List<String> sharedMessages = currentUser.getSharedMessages(); // get the shared messages of the current user
              sharedMessages.add(sharingMessage.split(":")[1].trim()); // add this new shared message
              currentUser.setSharedMessages(sharedMessages); // set the new shared messages list
              updateJSON(currentUser); // update JSON file
              
              DatagramPacket response = new DatagramPacket(sharingMessage.getBytes(), sharingMessage.length()); // create response packet
              response.setAddress(this.multicastAddr); // set response address to multicast address
              response.setPort(this.port); // set response port to multicast port
              this.multicastServer.send(response); // send the sharing message to multicast group
            }
          }          
        }
        
        sockIn.close(); // close the input stream of the server
        sockOut.close(); // close the output stream of the server
      }
      catch (SocketException e) {
        if (loggedIN) try {outLogger(currentUser.getUsername(), currentUser.getPassword());} catch (IOException ioe) {e.printStackTrace();}
      }
      catch (IOException e) {}

    }
  }

  // Check if user is already registered or not, then decide whether to register the user or not
  private static synchronized int checkUser(String username, String password) throws IOException { 
    Gson gson = new GsonBuilder().setPrettyPrinting().create(); // create a new GsonBuilder

    // Create an empty list of statistics for this user
    List<Integer> statistics = new ArrayList<>();
    statistics.add(0);
    statistics.add(0);
    statistics.add(0);
    statistics.add(0);

    // Create an empty HashMap of guess distribution statistics for this user
    ConcurrentHashMap<Integer, Integer> guessDistribution = new ConcurrentHashMap<Integer, Integer>();
    for (int i=1; i<13; i++) guessDistribution.putIfAbsent(i, 0); // initialize the guess distribution HashMap

    User user = new User(username, password, statistics, guessDistribution, new ArrayList<>(), new ArrayList<>()); //  create a new User
    
    // If the file containing all registered users is empty, then register the user
    if (registeredUsersJSON.length() == 0) {
      registeredUsersLIST.add(user); // add the user to the registered users list
      try (FileWriter writer = new FileWriter(registeredUsersJSON)) {gson.toJson(registeredUsersLIST, writer);} // update the "RegisteredUsers.json" file with the new user
      return 1; // return "1" for successful registration
    }

    try (FileReader reader = new FileReader(registeredUsersJSON)) {registeredUsersLIST = gson.fromJson(reader, new TypeToken<List<User>>() {}.getType());} // read the "RegisteredUsers.json" file and save all the registered users into the registered users list 

    // For each registered user check if the user is already registered
    for (User u: registeredUsersLIST) { 
      String userName = u.getUsername();

      if (username.equals(userName)) return 0; // return "0" for already registered user
    }

    registeredUsersLIST.add(user); // add the user to the registered users list
    try (FileWriter writer = new FileWriter(registeredUsersJSON)) {gson.toJson(registeredUsersLIST, writer);} // update the "RegisteredUsers.json" file with the new user

    return 1; // return "1" for successful registration
  }

  // Check if user is already logged in, then decide whether to log in the user or not
  private static synchronized int logger(String username, String password) throws IOException {
    Gson gson = new GsonBuilder().setPrettyPrinting().create(); // create a new GsonBuilder

    // If the file containing all logged in users is empty
    if (loggedUsersLIST.size() == 0) {
      if (registeredUsersJSON.length() == 0) return -1; // return "-1" if no one is registered or logged with this username and password

      try (FileReader reader = new FileReader(registeredUsersJSON)) {registeredUsersLIST = gson.fromJson(reader, new TypeToken<List<User>>() {}.getType());} // read the "RegisteredUsers.json" file and save all the registered users into the registered users list  

      // For each registred user check if the username and password of the logged in user match
      for (User registeredUser: registeredUsersLIST) { 
        String userName = registeredUser.getUsername(); 

        if (username.equals(userName)) {
          String passWord = registeredUser.getPassword();

          if (passWord.equals(password)) {
            loggedUsersLIST.add(registeredUser); // add the user to the logged users list
            return 1; // return "1" for successful log in
          }
        } 
      }

      return -1; // return "-1" for unsuccesful log in
    }

    try (FileReader reader = new FileReader(registeredUsersJSON)) {registeredUsersLIST = gson.fromJson(reader, new TypeToken<List<User>>() {}.getType());} // read the "RegisteredUsers.json" file and save all the registered users into the registered users list    

    // For each logged in user check if the user is already logged in
    for (User loggedUser: loggedUsersLIST) { 
      String userName = loggedUser.getUsername();
      
      if (username.equals(userName)) {
        String passWord = loggedUser.getPassword();
        
        if (passWord.equals(password)) return 0; // return "0" for already logged in user
      } 
    }
    
    // For each registered user check if the username and password of the logged in user match
    for (User registeredUser: registeredUsersLIST) { 
      String userName = registeredUser.getUsername();

      if (username.equals(userName)) {
        String passWord = registeredUser.getPassword();

        if (passWord.equals(password)) {
          loggedUsersLIST.add(registeredUser); // add the user to the logged users list
          return 1; // return "1" for successful log in
        }
      } 
    }

    return -1; // return "-1" for unsuccesful log in
  }

  // Check if user is already logged out, then decide whether to log out the user or not
  private static synchronized int outLogger(String username, String password) throws IOException {
    if (loggedUsersLIST.size() == 0) return -1; // return "-1" for unsuccesful log out beacause the user may have already logged out or not logged in yet

    // For each logged in user check if the username and password of the logged in user match
    for (User loggedUser: loggedUsersLIST) { 
      String userName = loggedUser.getUsername();

      if (username.equals(userName)) {
        String passWord = loggedUser.getPassword();

        if (passWord.equals(password)) {
          loggedUsersLIST.remove(loggedUser); // remove the user from the logged users list
          return 1; // return "1" for successful log out
        }
      } 
    }

    return -1; // return "-1" for unsuccesful log out
  }

  // Returns the user statistics
  private static synchronized List<Integer> getStatistics(String username, String password) throws IOException {
    List<Integer> statistics = new ArrayList<>(); // create a new empty List of statistics

    // For each logged in user check if the username and password of the logged in user match
    for (User loggedUser: loggedUsersLIST) { 
      String userName = loggedUser.getUsername();

      if (username.equals(userName)) {
        String passWord = loggedUser.getPassword();

        if (passWord.equals(password)) {
          statistics = loggedUser.getStatistics(); // get user statistics
        }
      }
    }

    return statistics; // return user statistics
  }

  // Check if user already played for current secret word, then decide to let him play or not
  private static synchronized int checkPlay(String username, String password) throws IOException {
    List<String> playedWordsL = new ArrayList<>(); // create a new empty List of registered user played words
    List<String> playedWordsR = new ArrayList<>(); // create a new empty List of logged user played words

    User logUser = null; // create a new logged user
    User regUser = null; // create a new registered user

    Gson gson = new GsonBuilder().setPrettyPrinting().create(); // create a new GsonBuilder

    try (FileReader reader = new FileReader(registeredUsersJSON)) {registeredUsersLIST = gson.fromJson(reader, new TypeToken<List<User>>() {}.getType());} // read the "RegisteredUsers.json" file and save all the registered users into the registered users list

    // For each registered user check if the username and password of the registered user match
    for (User registeredUser: registeredUsersLIST) { 
      String userName = registeredUser.getUsername();

      if (username.equals(userName)) {
        String passWord = registeredUser.getPassword();

        if (passWord.equals(password)) {
          playedWordsR = registeredUser.getPlayedWords(); // get user's played words list
          regUser = new User(registeredUser.getUsername(), registeredUser.getPassword(), registeredUser.getStatistics(), registeredUser.getGuessDistribution(), playedWordsR, new ArrayList<>()); // set the new registered user to this user with the played words list
        }
      }
    } 

    // For each logged in user check if the username and password of the logged in user match
    for (User loggedUser: loggedUsersLIST) { 
      String userName = loggedUser.getUsername();

      if (username.equals(userName)) {
        String passWord = loggedUser.getPassword();

        if (passWord.equals(password)) {
          playedWordsL = loggedUser.getPlayedWords(); // get user's played words list
          logUser = new User(loggedUser.getUsername(), loggedUser.getPassword(), loggedUser.getStatistics(), loggedUser.getGuessDistribution(), playedWordsL, new ArrayList<>()); // set the new logged in user to this user with the played words list
        }
      }
    } 

    // Check if user already played for current secret word
    for (String word: playedWordsL) {
      if (word.equals(secretWord)) return -1; // return "-1"
    }

    playedWordsL.add(secretWord); // add the current secret word to the registered user played words list
    playedWordsR.add(secretWord); // add the current secret word to the logged in user played words list
    logUser.setPlayedWords(playedWordsL); // set the new played words list to the logged in user
    regUser.setPlayedWords(playedWordsR); // set the new played words list to the registered user

    try (FileWriter writer = new FileWriter(registeredUsersJSON)) {gson.toJson(registeredUsersLIST, writer);} // update the "RegisteredUsers.json" file without the user

    return 1; //  return "1" to start the game play
  }

  // Update all JSON files
  private static synchronized void updateJSON(User user) throws IOException {
    Gson gson = new GsonBuilder().setPrettyPrinting().create(); // create a new GsonBuilder

    String username = user.getUsername(); // get username of the user
    String password = user.getPassword(); // get password of the user

    try (FileReader reader = new FileReader(registeredUsersJSON)) {registeredUsersLIST = gson.fromJson(reader, new TypeToken<List<User>>() {}.getType());} // read the "RegisteredUsers.json" file and save all the registered users into the registered users list

    int posR = 0; // search the correct position to replace the new user

    // For each registered user check if the username and password of the registered user match
    for (User registeredUser: registeredUsersLIST) { 
      String userName = registeredUser.getUsername();

      if (username.equals(userName)) {
        String passWord = registeredUser.getPassword();

        if (passWord.equals(password)) {
          registeredUsersLIST.set(posR, user); // replace the old user with the new one
        }
      }

      posR++;
    } 

    int posL = 0; // search the correct position to replace the new user

    // For each logged in user check if the username and password of the logged in user match
    for (User loggedUser: loggedUsersLIST) { 
      String userName = loggedUser.getUsername();

      if (username.equals(userName)) {
        String passWord = loggedUser.getPassword();

        if (passWord.equals(password)) {
          loggedUsersLIST.set(posL, user); // replace the old user with the new one
        }
      }

      posL++;
    }

    try (FileWriter writer = new FileWriter(registeredUsersJSON)) {gson.toJson(registeredUsersLIST, writer);} // update the "RegisteredUsers.json" file with the new user
  }

  // Returns current user
  private static synchronized User getCurrentUser(String username, String password) throws IOException {
    Gson gson = new GsonBuilder().setPrettyPrinting().create(); // create a new GsonBuilder

    User currentUser = null; // create a new user

    // If the file containing all logged in users is empty
    if (loggedUsersLIST.size() == 0) {
      try (FileReader reader = new FileReader(registeredUsersJSON)) {registeredUsersLIST = gson.fromJson(reader, new TypeToken<List<User>>() {}.getType());}  // read the "RegisteredUsers.json" file and save all the registered users into the registered users list

      // For each registered user check if the username and password of the registered user match
      for (User registeredUser: registeredUsersLIST) { 
        String userName = registeredUser.getUsername();
  
        if (username.equals(userName)) {
          String passWord = registeredUser.getPassword();
  
          if (passWord.equals(password)) {
            currentUser = registeredUser; // assign this registered user to the current user 
          }
        }
      } 

      return currentUser; // return the current user
    }

    // For each logged in user check if the username and password of the logged in user match
    for (User loggedUser: loggedUsersLIST) { 
      String userName = loggedUser.getUsername();

      if (username.equals(userName)) {
        String passWord = loggedUser.getPassword();

        if (passWord.equals(password)) {
          currentUser = loggedUser; // assign this logged in user to the current user 
        }
      }
    }

    return currentUser; // return the current user
  }

  // Search for the correct secret word number
  private static int getSecretWordNumber() {
    if (registeredUsersJSON.length() == 0) return 0; // if it's the first time the server starts

    Gson gson = new GsonBuilder().setPrettyPrinting().create(); // create a new GsonBuilder

    try (FileReader reader = new FileReader(registeredUsersJSON)) {registeredUsersLIST = gson.fromJson(reader, new TypeToken<List<User>>() {}.getType());}  // read the "RegisteredUsers.json" file and save all the registered users into the registered users list
    catch (IOException e) {}

    List<List<String>> listOfLists = new ArrayList<>(); // create a new empty List 
    List<String> flattenedList = new ArrayList<>(); // create a new empty List 
    List<String> sharedMessages = new ArrayList<>(); // create a new empty List 

    // For each registered user 
    for (User registeredUser: registeredUsersLIST) { 
      listOfLists.add(registeredUser.getSharedMessages()); // add to the list of lists each registered user's shared messages list
    }

    for (List<String> list: listOfLists) {
      flattenedList.addAll(list); // flatten the list, addAll() appends all of the elements in each list to the end of "flattenedList"
    }

    for (String sharedMessage: flattenedList) {
      sharedMessages.add(sharedMessage.split(" ")[1].trim()); // for each shared message get only the number of the secret word  
    }

    Collections.sort(sharedMessages); // sort the shared messages list

    return sharedMessages.size() == 0 ? 0 : Integer.parseInt(sharedMessages.get(sharedMessages.size() - 1)); // if no one shared some messages return "0" otherwhise the last element of the sorted list (greatest secret word number)
  }

  // Returns the guessed word crypted with hints for the user
  private static synchronized String getHints(String guessedWord) {
    String hintWord = ""; // create a new empty string
    char[] match = new char[guessedWord.length()]; // matching characters array

    // Create a ConcurrentHashMap to save each secret word's character occurrences 
    ConcurrentHashMap<String, Integer> occurrencesS = new ConcurrentHashMap<>();

    for (int i=0; i<secretWord.length(); i++) occurrencesS.putIfAbsent(String.valueOf(secretWord.charAt(i)), 0); // initialize the secret word's occurrences HashMap

    // Count all occurrences
    for (int i=0; i<secretWord.length(); i++) {
      int oldVal = occurrencesS.get(String.valueOf(secretWord.charAt(i))); // get old value of this character from the HashMap
      occurrencesS.replace(String.valueOf(secretWord.charAt(i)), ++oldVal); // then replace it with incremented value
    }

    // Create a ConcurrentHashMap to save each guessed word's character occurrences 
    ConcurrentHashMap<String, Integer> occurrencesG = new ConcurrentHashMap<>();

    for (int i=0; i<guessedWord.length(); i++) occurrencesG.putIfAbsent(String.valueOf(guessedWord.charAt(i)), 0); // initialize the guessed word's occurrences HashMap

    // Search for matching characters
    for (int i=0; i<secretWord.length(); i++) {
      if (secretWord.charAt(i) == guessedWord.charAt(i)) {
        match[i] = guessedWord.charAt(i); // add the matching charater in the correct place
        int oldVal = occurrencesG.get(String.valueOf(guessedWord.charAt(i))); // get old value of this character from the HashMap
        occurrencesG.replace(String.valueOf(guessedWord.charAt(i)), ++oldVal); // then replace it with incremented value
      } 
    }

    // Create the hint word
    for (int i=0; i<secretWord.length(); i++) {
      // Increase the value of the occurrences only if this isn't a matched character
      if (guessedWord.charAt(i) != (match[i])) {
        int oldVal = occurrencesG.get(String.valueOf(guessedWord.charAt(i))); // get old value of this character from the HashMap
        occurrencesG.replace(String.valueOf(guessedWord.charAt(i)), ++oldVal); // then replace it with incremented value
      }
      
      if (secretWord.charAt(i) == guessedWord.charAt(i)) hintWord += '!'; // if the character is in the correct place then replace it with "!"

      if ((secretWord.indexOf(guessedWord.charAt(i)) != -1) && (secretWord.charAt(i) != guessedWord.charAt(i))) {
        // If I have encountered this character fewer times than there are occurrences in the secret word
        if (occurrencesG.get(String.valueOf(guessedWord.charAt(i))) <= occurrencesS.get(String.valueOf(guessedWord.charAt(i)))) hintWord += '?'; // if the character is not in the correct place but it's in the secret word then replace it with "?"
        else hintWord += '-'; // if the character isn't in the secret word then replace it with "-"
      }

      if (secretWord.indexOf(guessedWord.charAt(i)) == -1) hintWord += '-'; // if the character isn't in the secret word then replace it with "-"
    }

    return hintWord; // return the hint word
  }
}