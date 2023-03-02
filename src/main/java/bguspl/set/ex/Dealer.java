package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.Util;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.concurrent.*;
import javax.naming.spi.DirStateFactory.Result;
import java.util.Random;
import java.util.LinkedList;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;
    private Thread dealerThread;
    /**
     * to give premission to players to put tokens
     */
    private volatile boolean okPlaceTokens = false;
    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;
    /**
     * True if game should be terminated due to an external event.
     */
    private volatile boolean terminate = false;
    /**
     *  lock is used to synchornized between dealer to the players
     */

    private final Object lock;
    /**
     *  true if the set of last check player is valid
     */
    private boolean isValidSet;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.lock = new Object();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        dealerThread = Thread.currentThread();
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        for (int i = 0; i < players.length; i++) {
            Thread playerThread = new Thread(players[i], "player" + i);
            playerThread.start();
        }
        while (!shouldFinish()) {
            placeCardsOnTable();
            if (!terminate) {
                this.okPlaceTokens = true;
            }
            updateTimerDisplay(true);
            timerLoop();
            this.okPlaceTokens = false;
            removeAllCardsFromTable();
        }
        announceWinners();
        terminate();
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            checkPlayersInQueue();
        }
    }
     /**
     * The inner loop going through the queue that hold players to check their sets 
     */
    private void checkPlayersInQueue() {
        while (!table.PlayersToCheck.isEmpty()) {
            try {
                int idPlayer = table.PlayersToCheck.take();
                int[] set = generatePlayerSet(idPlayer);
                if (env.util.testSet(set)) {
                    players[idPlayer].setIsValidSet(true);
                    players[idPlayer].setInCheck(false);
                    this.okPlaceTokens = false;
                    removeCardsFromTable(idPlayer);
                    placeCardsOnTable();
                    updateTimerDisplay(true);
                    this.okPlaceTokens = true;
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                } else {
                    players[idPlayer].setInCheck(false);
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                }
            } catch (InterruptedException e) {
            }
        }
    }
     /**
     * This method generate the set a player sent that should be checked 
     * @param id : the id of the player who sent the request
     * @return set of three card (not speciflcally valid)
     */
    private int[] generatePlayerSet(int id) { 
        BlockingQueue<Integer> copyOfPlayerTokens = new LinkedBlockingDeque<Integer>(players[id].getMyTokens());
        int[] set = new int[env.config.featureSize];
        if (copyOfPlayerTokens.size() == env.config.featureSize) {
            try {
                for (int i = 0; i < env.config.featureSize; i++) {
                    set[i] = table.slotToCard[copyOfPlayerTokens.take()];
                }
            } catch (InterruptedException e) {
            }
        }
        return set;
    }
    /**
     * This method checks if their are more valid sets in game or should finish
     * @return boolean value to finish the game or not
     */
    private boolean isFinishedGame() {
        boolean ans = false;
        List<Integer> leftCardsOnTable = new LinkedList<Integer>();
        if (env.util.findSets(deck, 1).size() == 0) {
            for (int i = 0; i < table.slotToCard.length; i++) {
                if (table.slotToCard[i] != null)
                    leftCardsOnTable.add(table.slotToCard[i]);
            }
            if (env.util.findSets(leftCardsOnTable, 1).size() == 0)
                ans = true;
        }
        return ans;
    }
/**
     * This next few methods are get methods of fields
     * @return the fields
     */
    public Thread getThread() {
        return dealerThread;
    }

    public Object getLock() {
        return lock;
    }

    public boolean getIsValidSet() {
        return isValidSet;
    }

    public boolean getOktoPutTokens() {
        return this.okPlaceTokens;
    }
    public List<Integer> getDeck()//used for tests
    {
        return this.deck;
    }
    public long getReShuffle() //used for tests
    {
        return this.reshuffleTime;
    }
    /**
     * set the value of isValisSet field
     * @param a: the new value
     * @post: isValidSet changed to value a
     */
    public void setIsValidSet(boolean a) {
        this.isValidSet = a;
    }
    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        for (int i = players.length - 1; i >= 0; i--) {
            players[i].terminate();
        }
        this.terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true if the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }
    /**
     * This method removes cards from table (only if a set as been declared)
     * @param playerID : the id of the player who successfully reached a set
     * @post : in the env.config.featureSize slots of the set, cards are out from game, and all tokens has been removed
     */
    private void removeCardsFromTable(int playerID) {
        while (!players[playerID].getMyTokens().isEmpty()) { // Emptying myTokens field
            int slot = players[playerID].getMyTokens().remove();// Updating myTokens after remove
            env.ui.removeTokens(slot); // Clears token from card
            table.getTokensInSlot()[slot].remove(playerID);
            int sizeOfQueue = table.getTokensInSlot()[slot].size();
            for (int j = 0; j < sizeOfQueue; j++) {// For each player who is not "playerId"
                try {
                    int i = (int) table.getTokensInSlot()[slot].take();
                    if (table.PlayersToCheck.contains(i)) {// If player "i" had a env.config.featureSize tokens, not anymore
                        table.PlayersToCheck.remove(i);
                        players[i].setInCheck(false);
                    }
                    players[i].getMyTokens().remove(slot); // Updating myTokens after remove
                } catch (InterruptedException e) {
                }
            }
            table.removeCard(slot);
        }
    }

    /**
     *  placed on the table cards in empty slots. public for tests purpose
     * @pre : some empty slots (or not)
     * @post : all previous empty slots with cards if possible (enough cards in deck)
     */
    public void placeCardsOnTable() {
        int i = 0;
        Random rnd = new Random();
        while (!deck.isEmpty() && i < table.slotToCard.length) { // Do not extend over 12 cards, and procceed only if there are cards in deck
            if (table.slotToCard[i] == null) { // meaning no card is in the slot
                int card = deck.remove(rnd.nextInt(deck.size())); // take a card from deck
                table.placeCard(card, i);
            }
            i++;
        }
        this.terminate = isFinishedGame();
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private synchronized void sleepUntilWokenOrTimeout() {

        if (this.reshuffleTime - System.currentTimeMillis() > env.config.turnTimeoutWarningMillis) {
            try {
                this.wait(900);
            } catch (InterruptedException e) {
            }

        }
    }

     /** public for tests purpose
     * this method update the timer (reset it or change graphic if under 5 seconds)
     * @param reset : boolean to reset the timer or not
     * @pre : need reset/don't need reset and:time greater than 5 sec/time under 5 sec
     * @post : reseting or changing graphic 
     */
    public void updateTimerDisplay(boolean reset) {
        if (reset) {
             this.reshuffleTime = System.currentTimeMillis() +
             env.config.turnTimeoutMillis;
        }
        long leftTime = this.reshuffleTime - System.currentTimeMillis();
        if (leftTime < env.config.turnTimeoutWarningMillis) {
            env.ui.setCountdown(leftTime, true);
        } else {
            env.ui.setCountdown(leftTime, false);
        }
    }

    /**
     * Returns all the cards from the table to the deck. public for tests purpose
     */
    public void removeAllCardsFromTable() {
        // Clearing table fields
        for (int j = 0; j < table.getTokensInSlot().length; j++) {
            table.getTokensInSlot()[j].clear();
        }
        // Clearing players field
        for (int k = 0; k < players.length; k++) {
            players[k].getMyTokens().clear();
            players[k].setInCheck(false);
        }
        env.ui.removeTokens();
        int i = 0;
        while (i < table.slotToCard.length) {
            deck.add(table.slotToCard[i]);
            table.removeCard(i);
            i++;
        }
    }

     /**
     * set field okPlaceTokens - only for tests
     * @param a : value to change field to give premission to players to put tokens
     * @post okPlaceTokens value changed to a
     */
    public void setplaceTokens(boolean a)
    {
        this.okPlaceTokens=a;
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        List<Integer> ans = new LinkedList<Integer>();
        int max = players[0].score();
        for (int i = 1; i < players.length; i++) {
            if (players[i].score() > max)
                max = players[i].score();
        }
        for (int j = 0; j < players.length; j++) {
            if (players[j].score() == max) {
                ans.add(players[j].id);
            }
        }
        int[] finalArray = new int[ans.size()];
        for (int i = 0; i < finalArray.length; i++) {
            finalArray[i] = ans.get(i);
        }
        env.ui.announceWinner(finalArray);
    }
}
