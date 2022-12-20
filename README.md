
# Wordle 3.0

The source code of the project (Wordle 3.0) is divided into 4 Java
files, of which each performs a certain function. The files are
named as follows:

- WordleServerMain.java: guessable from the mnemonic name, this file fulfills the role of the Server and inside it is the static method main() whose task is to open the necessary connections, select a random word in a repeated interval of 5 minutes and manage a thread pool (size unlimited), of which each thread plays the role of "acceptor" (as suggested by the name of the class inside this file) of clients who want to start a match.

- WordleClientMain.java: this file also contains the static main() method but performs the role of the Client. The task of this code is to establish a connection with the Server, acting as a conduit, allowing a communication with the end user. The Client is then responsible for handling the following commands (verifying they are entered correctly) entered by the user:

        1. Request by the user to register;
        2. Request by the user to log in;
        3. Request by the user to log out;
        4. Request by the user to play a game (trying to guess the Secret Word, entering the Guessed Word);
        5. Request by the user to receive statistics about the games played;
        6. Request by the user to share the result of the match just finished;
        7. Request by the user to view the notifications sent by all the users belonging to the social group (related to its log in phase);

- MulticastHandler.java: this file contains the code related to a thread, scheduled by the Client once the generic user has passed the login phase. In the run() method, such a thread continuously waits for "notifications" from the Server, which sends such notifications regarding the outcome of ageneric user's match only if that user has requested the view via command 7.

- User.java: represents what the basic characteristics of a user are and contains getter and setter methods. The characteristics related to a given user are:

        - Username;
        - Password;
        - Statistics;
        - Words played;
        - Shared messages;

However, the project is composed of additional files that allow its ability difunction "autonomously" (user interaction excluded). Such files are:

- RegisteredUsers.json: keeps track of all registered users, storing them in JSON format. It allows users to persist in the system even if for any whatever reason, the Server goes down or offline. It allows also the Server to restart from the last game state it was in, simply by reading the contents of that file.

- ServerConfig.txt: configuration file for the Server. It contains 3 lines, the first refers to the port that the Server must connect to in order to establish a connection with the Client, the second is a multicast address between 224.0.0.0.0 (excluded as reserved) and 239.255.255.255 (included), and the third is a timer (expressed in milliseconds) that sets the time interval between the extraction of one Secret Word and another.

- ClientConfig.txt: configuration file for the Client. It too contains 3 lines, the first and second refer to the address ("localhost") and port (same as the Server's) to which the Client must connect to establish a connection with the Server, the third line contains the multicast address.

## Implementation choices

- WordleServerMain.java: as already anticipated, the Server takes care of three main tasks:

        - Extract a Secret Word (random) within the file, every 5 minutes
        - Manage a thread pool.
        - Implement an Accepter class that handles interaction with Clients.

To extract a Secret Word from the file randomly I, first, inserted
each line of the file inside a list via the method 
readAllLines(Path path) of the Files class, which reads and 
returns all the lines contained in the file in a list. Immediately
after that I decided to manage the time interval between 
extractions via a timer (from the Timer class), which once 
scheduled would execute a given TimerTask. I managed such 
scheduling of the timer via the scheduleAtFixedRate(TimerTask task, longdelay, long period) 
method, which schedules the task specified by the parameter 
"task" for repeated execution with fixed interval, starting 
from the delay specified in the parameter "delay". Subsequent 
executions take place atapproximately regular intervals, separated
by the period specified in the parameter “period”. Within of the 
method is the run() method (since a TimerTask implements the 
Runnable interface), in which I extract a Secret Word randomly 
from the previously created list via the get(int index) method, 
which allows the extraction of an element (in this case random 
via newRandom().nextInt(int bound) which generates a random number
between 0 (inclusive) and "bound" (excluded)) of the list from 
the position indicated by the parameter "index". The management 
of the thread pool (implemented as CachedThreadPool) is done 
within the method main() in a while(true) loop. At each 
iteration the thread pool tries to execute the command passed 
to it as a parameter (execute(Runnable command)) which in this 
case is the creation of the anonymous instance of the Accepter class
(it is always found in this file) which has the primary task 
of "accepting" the connection request from a given Client.
The Accepter class, once the connection with the Client is 
established, takes care of the of the actual interaction with it. 
I have chosen for simplicity implementation the Client-Server 
version of java.io so both connect via Socket and communicate 
thanks to a shared write channel (getOutputStream()) and read (getInputStream()). 
The Accepter class implements the Runnable interface so it can be executed by threads in the thread pool
managed by main() , so it has a run() method within which the 
Server communicates with the Client. The running thread is always 
waiting for messages from the Client via a readLine() of the 
input stream related to the socket within a while(true) loop and 
divides the requests received from the client according to the
type:

        1. Request from the Client to register a user;
        2. Request from the Client to log in a user;
        3. Request by the Client to log out a user;
        4. Request by the Client to have a user play;
        5. Request by Client to allow a user to send a Guessed Word;
        6. Request by the Client to receive a user's statistics;
        7. Request by the Client to share the outcome of a user's match to the social group;

Actually, in the Client, request "5" is automatically 
"incorporated" into request "4" but only in the case where the 
user has never played for the current Secret Word currently, 
then the game begins. Otherwise, an error message is reported 
telling the user that he has already played for that Secret Word 
and that he must wait for the word change.
For each type of request, the "thread-acceptor" responds 
differentlyusing private methods of the WordleServerMain class. 
Each method performs a certain function specific to the type of 
request:

- Request type "1" is associated with the phase of registering a user and this is accomplished by a check on the password (it must not be empty) followed by a call to the method checkUser(String username, String password) which checks if a user is already registered. I chose to implement the set of registered/logged-in users via two separate lists, RegisteredUsersLIST for registered users and LoggedUsersLIST for users who have successfully logged in. Since lists in Java are not thread safe, I decided to synchronize accesses to them by the keyword synchronized whenever a thread accesses anymethod within the WordleServerMain class, to ensure mutualexclusion. Returning to the registration method, it checks the content of the RegisteredUsers.json file, if it is empty it simply adds the user to the list of registered users and writes that list to the file via themethod toJson(Object src, Appendable writer) provided by the GSON library. If thefile is not empty, it means that there is at least one registered user in the system,so it must check that the user it is trying to register isnotalready contained in the file. It then reads the contents of the file via the method fromJson(Reader json, Type typeOfT) (again provided by the GSON library) and stores it in the list of registered users, scrolls through that list and if it finds a userwith the same username it sends an error code to the Client which will notify the user that he is already registered.

- Request type "2" is associated with the phase of logging in a user, which is initiated by calling the method logger(String username, String password), which performs a number of checks. It first checks whether the list of logged in users is empty or not, if it is, it checks that the list of registered users is not also empty (otherwise it sends an error message a that tells the user that he is not yet logged in), then proceeds with the check that the user is actually logged in, and if so, adds him/her to the list of logged in users. In case that list is not empty, it checks if the credentials entered are correct and correspond to a user within it, returning a code related to successful log in.

- Request type "3" is associated with the phase of logging out a user, which is done by calling the method outLogger(String username, String password) which, initially, checks that the list of logged in users is not empty (if not, it sends an error message informing the user that he is not logged in yet), then proceeds with the check that the user is actually logged in and if so, it removes him from the list of logged in users, returning a code related to the successful log out. If it does not find any user with such credentials it returns an error codethat informs the user that he has not yet logged in.

- Request type "4" is associated with the phase of preparing for game play, which is done by calling the method checkPlay(String username, String password) that checks whether the user has already played for the Secret Word that is still running. To do this it checks the list of the words played by the user (characteristic of each user saved in the RegisteredUsers.json file) and if it finds within it the Secret Word it returns an error code that tells the user that he has already played for that particular word. If not, it adds the current Secret Word to the list of played words.

- Request type "5" is associated with the actual game play. Once this prompt is received, the 12 attempts begin to allow the user to guess the Secret Word by entering the Guessed Word. At each iteration it is checked whether the user guessed (by sending a code related to winning), lost (by sending a code related to failing to guess the Secret Word), or if the timer expired while the user was playing, so the word changed. In the first and second cases it simply updates that user's statistics. In the last case, a check is made that the last word played by the user is not different from the current Secret Word, since if it is, it would mean that the Secret Word has changed but the user was still playing the old one. Therefore, it alerts the user that the word has changed, removes the last word from the list of words played, and updates the JSON file by stopping the current iteration and listening for a new request from the Client.

- Request type "6" the Server responds by sending the statistics of queldetermined user by calling the method getStatistics(String username, String password) which searches for the user in the list of logged-in users and retrieves the list of statistics.

- Request type "7" request, the Server responds (via multicast) by sending a notification (which will be "listened to" by a thread started by the Client after a successful log in) regarding the outcome of the game played by the user to a social group. The RegisteredUsers.json file keeps track of all the outcomes that each user has decided to share but each user is able to view only the results shared from his log in session until his logout session (what is sent before or after is not considered).

Within the WordleServerMain class are other methods that allow the state of the game to be reconstructed at all times, such as the
updateJSON(User user) that updates the contents of the RegisteredUsers.jsons file by replacing the user who has the same credentials as "user", with "user" itself. The getCurrentUser(String username, String password) method, on the other hand, allows you to get the most up-to-date version of the current user. The method
getSecretWordNumber() method allows the Server every time it is restarted to retrieve the number of the last Secret Word extracted, but if no user has shared any results it restarts from 0. The method getHints(String guessedWord) checks the Guessed Word sent during the game play phase and extracts a Hint Word, i.e. a word that allows the Client to convert the hints into colors to print the clues (colored letters) in the console, green color corresponds to the character "!", yellow color corresponds to the character "?" and gray color corresponds to the character "-".

- WordleClientMain.java: the Client plays an intermediate role between the Server and the end user. It deals with the interaction between the two and to do this it needs to establisha connection with the Server via Socket. The Client accepts from the command line a series of requests from the user that allow the user to:

        1. Register;
        2. Log in;
        3. Log out;
        4. Play the game;
        5. Receive statistics;
        6. Share the results of the last game;
        7. View all shared results (including those of other users) from its log in;

The Client then reads from the command line the user's request (checking its correctness, i.e., that it is indeed a number and that it is between 1 and 7) and based on the number it writes, performs a certain procedure.

- Request type "1" is associated with the registration phase, in which the Client asks the user to enter credentials and then sends (in the following order) request type and credentials to the Server. This process of communication with the Server occurs during the call to the method register(String username, String password, Socket client, BufferedReader sockIn, PrintWriter sockOut) by which the Client reads the result returned by theServer (as a result of the checks).

- Request type "2" is associated with the log in phase, in which the Clien asks the user to enter the credentials again to then send (in the following order) request type and credentials to the Server. This process of communication with the Server occurs during the call to themethod login(String username, String password, Socket client, BufferedReader sockIn, PrintWriter sockOut) thanks to which the Client reads the result returned by the Server (as a result of the checks).

- Request type "3" is associated with the log in phase, in which the Clien asks the user is sure he/she wants to log out, if the answer is affirmative the Client sends (in the following order) request type and credentials (it does not need to ask for credentials again because once the user has logged in, the Client can keep track of the current user) to the Server. This process of communication with the Server starts while calling the method logout(String username, String password, Socket client, BufferedReader sockIn, PrintWriter sockOut) thanks to which the Client reads the result returned by the Server (as a result of the checks).

- Request type "4" is associated with the game play. The Client, before allowing the user to play the game, needs to ask the Server if that user has already played for the current Secret Word and to do this it calls themethod playWordle(String username, String password, Socket client, BufferedReader sockIn, PrintWriter sockOut) thanks to which it sends to the Server the type of the request and the credentials of the user who wants to play the game and reads the response result. Depending on the result received it decides whether to let the user play or not, in case of a positive result it sends to the Server a request of type "5" (this is not the type "5" request of the Client requesting the statistics but for the Server, as seen above, a type "5" request allows the user to send the Guessed Word) and the game play begins. At this point the user must enter the Guessed Word (10-character word) and have a maximum of 12 attempts to guess the Secret Word. Once the word is entered, the Client sends it to the Server by calling the method sendWord(Socket client, BufferedReader sockIn, PrintWriter sockOut, String guessedWord), by which the Client reads the result returned by the Server (as followed by the checks). If the result is positive (the word is in the list of secret words in the words.txt file), the Server sends the Client a word (HintWord), representing the Guessed Word, but with clues to help the user guess the Secret Word. I decided to make such a hint pattern as similar as possible without implementing a graphical interface but simply coloring the decoded letters of the Hint Word and showing them on the screen. To do this I followed the coloring used by the original Wordle game, but via ANSI escape:
        
        - String ANSI_RESET = "\u001B[0m" : deactivates all ANSI attributes set up to that point, returning the console to default values.
        - String ANSI_YELLOW = "\u001B[33m" : sets the color to yellow.
        - String ANSI_GREEN = "\u001B[32m" : sets the color to green.

The terminal interprets these sequences as commands, rather 
than as text to be simply displayed. To "decode" the Hint Word, 
I use the method colorWord(String guessedWord, String hintWord) 
in which, for each character of the Hint Word, I print via 
print() that character but decoded and colored. The character "!" 
is associated with the color green, the character "?" is 
associated with yellow, and if the character "-" is encountered 
simply decode it by taking the corresponding one in the Guessed 
Word and printing it (since by default the terminal prints 
with the colorgrey).

- Request type "5" is associated with the user's request for suestatistics. The Client makes a call to the method sendMeStatistics(String username, String password, Socket client, BufferedReader sockIn, PrintWriter sockOut) through which it sends request type and user credentials to theServer and reads, one after another, the various statistics showing them to the user.

- Request type "6" is associated with the request for the user to share the result of his match. The Client, then, calls the method share(PrintWriter sockOut) which sends the type of the request to the Server (nothing is shown as a result of this request).

- Request type "7" is associated with the request, part of the user, to display all the results shared in the social group. To do this, the Client creates a thread in main() but will send it to execution only after the user has passed the log in phase. Once that phase is passed, the Client will joins the social group and from then on is able to receive the results shared by all users. The thread that has been started then goes then listening for notifications from the Server, which sends the group social group the notification shared by a particular user after the request to sharing ("6" in the Client and "7" in the Server).

As for checking that there are no inconsistencies between the various requests (e.g., a user who wants to log out before logging in, or a user who wants to play without having logged in) I decidedto handle such situations through a series of Boolean variables that are initially set to false but once the "critical" stages are passed are set torue (e.g., the registered variable checks that a user is registered,so initially it is false but once the user registers it is set torue).

- MulticastHandler.java: this is the thread, activated by the Client, that listens (continuously) for notifications from the Server regarding the outcomes of the users' parties. Being a thread, it must implement the Runnable interface and have a run() method . To store all notifications it uses a list within which it inserts a notification once it has been received.In addition to the run() method , it also has a public showNotification() , used by the Client to display all received notifications.

- User.java: is the class that represents an object of type User. It has getter and setter methods to be able to update the content of its attributes (such as the list of statistics or words played) at any time.


## Usage

The source code part of the project branches into three folders:

- bin: contained within it are the files necessary for the code to function. Source, such as text files, files in JSON format, and compilation files (file.class).

- libs: contains the external libraries in jar format, in this case the only library external library used is GSON, so inside it is a file named "gson-2.10.jar."

- src: contains the source code (file.java) divided into the four files explained above. It is assumed to be in the initial Wordle folder (the path should be more or less C:\...\Wordle)

As for the compilation (via javac) you have to, first,
move to the src folder (the path should be more or less C:\...\Wordle\src)
via the command:

```bash
cd src 
```

Then, complete by specifying the classhpaths of the external libraries used and the destination folder of the ".class" files using the following syntax:

```bash
$javac -cp classpath ./*.java -d directory 
```
- **cp** *classpath*: specifies the path used by javac to search for classes needed to run javac or referenced by other classes being compiling. It replaces the default or the environment variable CLASSPATH, if set. The classpath parameter is the path in which the the external library is located (in this case I used the path .;./../libs/gson-.2.10.jar).

- **d** *directory*: specifies the root directory of the ".class" file hierarchy. The directory parameter is essentially the destination directory for compiled classes (in this case I used the bin directory as the destination, so ../../bin).

Once the compilation is complete we are in the src directory. To run the code we must then move to the bin folder, we execute the following commands:

```bash
cd..
cd bin
```

Once we enter the bin folder (the path should be more or less C:\...\Wordle\bin) we are ready to execute the code, so we open two separate terminals (one for the Server and one for the Client and run with the following syntax:

```bash
$java -cp classpath WordleServerMain
```

```bash
$java -cp classpath WordleClientMain
```

Here again we need to specify the classpath (which in this case is equal to before .;./../libs/gson-2.10.jar ) and the "-cp" option allows us to do this. The last path is the name of the class we want to run (excluding the".java" extension).
