// Utility imports
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class User {
  private String username; 
  private String password;
  private List<Integer> statistics;
  private ConcurrentHashMap<Integer, Integer> guessDistribution;
  private List<String> playedWords;
  private List<String> sharedMessages;

  public User(String username, String password, List<Integer> statistics, ConcurrentHashMap<Integer, Integer> guessDistribution, List<String> playedWords, List<String> sharedMessages) {
    this.username = username;
    this.password = password;
    this.statistics = statistics;
    this.guessDistribution = guessDistribution;
    this.playedWords = playedWords;
    this.sharedMessages = sharedMessages;
  }

  // Returns the username
  public String getUsername() {
    return this.username;
  }

  // Returns the password
  public String getPassword() {
    return this.password;
  }

  // Returns the statistics list
  public List<Integer> getStatistics() {
    return this.statistics;
  }

  // Returns the guessDistribution
  public ConcurrentHashMap<Integer, Integer> getGuessDistribution() {
    return this.guessDistribution;
  }

  // Returns the played words
  public List<String> getPlayedWords() {
    return this.playedWords;
  }

  // Returns the shared messages
  public List<String> getSharedMessages() {
    return this.sharedMessages;
  }

  // Set the statistics list
  public void setStatistics(Integer games, Integer wins, Integer currentStreak, Integer maxStreak) {
    List<Integer> stats = new ArrayList<Integer>();
    stats.add(games);
    stats.add(wins);
    stats.add(currentStreak);
    stats.add(maxStreak);

    this.statistics = stats;
  }

  // Set the guess distribution
  public void setGuessDistribution(ConcurrentHashMap<Integer, Integer> update) {
    this.guessDistribution = update;
  }

  // Set the played words list
  public void setPlayedWords(List<String> playedWords) {
    this.playedWords = playedWords;
  }

  // Set the shared messages list
  public void setSharedMessages(List<String> sharedMessages) {
    this.sharedMessages = sharedMessages;
  }
}