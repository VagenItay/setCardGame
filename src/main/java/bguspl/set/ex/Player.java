package bguspl.set.ex;

import bguspl.set.Env;
import java.util.concurrent.*;
import java.util.Random;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    // Player actions queue
    private BlockingQueue<Integer> actionsQueue;
    // My tokens
    private BlockingQueue<Integer> myTokens;

    // token counter of my Player
    // private AtomicInteger tCounter;
    private volatile boolean isWaitingAI;
    private volatile boolean isValidSet = false;
    private volatile boolean inCheck = false;
    private volatile boolean isFreezed = false;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate
     * key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;
    /**
     * The dealer of the game.
     */
    private Dealer dealer;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided
     *               manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.actionsQueue = new LinkedBlockingDeque<Integer>(env.config.featureSize);
        this.myTokens = new LinkedBlockingDeque<Integer>(env.config.featureSize);
        this.dealer = dealer;
    }

    /**
     * The main player thread of each player starts here (main loop for the player
     * thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        System.out.printf("Info: Thread %s starting.%n",
                Thread.currentThread().getName());
        if (!human)
            createArtificialIntelligence();
        while (!terminate) {
            try {
                int slot = -1;
                if (!human && isWaitingAI) {
                    synchronized (this) {
                        slot = actionsQueue.take();
                        if (!dealer.getOktoPutTokens()) {
                            actionsQueue.clear();
                            this.isWaitingAI = false;
                            slot = -1;
                        }
                        if (slot != -1) {
                            if (myTokens.contains(slot)) { // If my token exsits in this slot - Remove it
                                playerRemovingToken(slot);
                                this.isWaitingAI = false;
                            } else { // If my token does not exsit in this slot - Add it
                                if (myTokens.size() < env.config.featureSize) {
                                    boolean ans = playerPlacingToken(slot);
                                    this.isWaitingAI = false;
                                    if (ans) {
                                        if (myTokens.size() == env.config.featureSize) {
                                            isValidSet = false;
                                            inCheck = true;
                                            table.PlayersToCheck.put(id);
                                            try {
                                                synchronized (dealer.getLock()) {
                                                    synchronized (dealer) {
                                                        dealer.notify();
                                                    }
                                                    while (inCheck) {
                                                       
                                                        dealer.getLock().wait();
                                                    }
                                                }
                                            } catch (InterruptedException e) {
                                            }
                                            if (isValidSet) {
                                                this.point();
                                            } else {
                                                this.penalty();
                                            }
                                        }
                                    }
                                } 
                            } 
                        } 
                        this.notify();
                    }
                } else if (human) {
                    slot = actionsQueue.take(); // Taking action from queue of Incoming Actions
                    if (myTokens.contains(slot)) { // If my token exsits in this slot - Remove it
                        playerRemovingToken(slot);
                    } else { // If my token does not exsit in this slot - Add it
                        if (myTokens.size() < env.config.featureSize) {
                            boolean ans = playerPlacingToken(slot);
                            if (ans) {
                                if (myTokens.size() == env.config.featureSize) {
                                    isValidSet = false;
                                    inCheck = true;
                                    table.PlayersToCheck.put(id);
                                    try {
                                        synchronized (dealer.getLock()) {
                                            synchronized (dealer) {
                                                dealer.notify();
                                            }
                                            while (inCheck) {
                                                dealer.getLock().wait();
                                            }
                                        }
                                    } catch (InterruptedException e) {
                                    }
                                    if (isValidSet) {
                                        this.point();
                                    } else {
                                        this.penalty();
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
            }
        }
        if (!human)
            try {
                aiThread.join();
            } catch (InterruptedException ignored) {
            }
        System.out.printf("Info: Thread %s terminated.%n",
                Thread.currentThread().getName());
    }
    /**
     * this method is used only for testing.  
     * @return - actions blocking queue.
     */
    public BlockingQueue getMyActions()
    {
        return this.actionsQueue;
    }
    /**
     * this method is used only for testing. 
     * @param - new Freezed value.
     */
    public void setIsFreezed(boolean input)
    {
        this.isFreezed=input;
    }
    /**
     * this method is used only for testing.  
     * @return - IsFreezed value.
     */
    public boolean getIsFreezed()
    {
        return isFreezed;
    }
    /**
     * this method is used to place token on slot.
     * @param - slot to put token on. 
     * @pre - if able to put token, the token is placed on the slot, and myTokens is updated 
     * @return - boolean value to know if really put token on slot
     */
    private boolean playerPlacingToken(int slot) {
        boolean ans = false;
        if (table.slotToCard[slot] != null && dealer.getOktoPutTokens()) {
            ans = table.placeToken(id, slot);
            try {
                if (ans) {
                    myTokens.put(slot);
                }
            } catch (InterruptedException e) {
            }
        }
        return ans;
    }
    /**
     * this method is used to remove token from slot.
     * @param - slot to remove token from. 
     * @pre - if able to remove token from table,myTokens is updated 
     */
    private void playerRemovingToken(int slot) {
        boolean ans = table.removeToken(id, slot);
        if (ans) {
            myTokens.remove(slot);
        }
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of
     * this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it
     * is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            Random rnd = new Random();
            while (!terminate) { // Key press simulator
                int slot = rnd.nextInt(table.slotToCard.length) + 0;
                if (keyPressed(slot)) {
                    try {
                        synchronized (this) {
                            isWaitingAI = true;
                            this.wait();
                        }
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    public boolean getInCheck() {
        return this.inCheck;
    }

    public void setInCheck(boolean a) {
        this.inCheck = a;
    }

    public BlockingQueue<Integer> getMyTokens() {
        return myTokens;
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        this.terminate = true;
        playerThread.interrupt();
        try {
            playerThread.join();
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     * @return - boolean to know if successfully updated actionsQueue
     */
    public boolean keyPressed(int slot) {
        boolean ans = false;
        if (dealer.getOktoPutTokens()) {
            try {
                if (table.slotToCard[slot] != null && !inCheck && !isFreezed
                        && (myTokens.size() < env.config.featureSize || myTokens.contains(slot))) {
                    actionsQueue.put(slot);
                    ans = true;
                }
            } catch (InterruptedException e) {
            }
        } else {
            actionsQueue.clear();
        }
        return ans;
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        this.score++;
        env.ui.setScore(id, score);
        freezeTime(env.config.pointFreezeMillis);
        isFreezed = false;
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
    }
    /**
     * freeze time for player.
     * @param freezeTime: time to be in freeze
     * @post player returns to the game after spent time in freeze
     */ 
    
    private void freezeTime(long freezeTime) {
        isFreezed = true;
        long time = System.currentTimeMillis();
        while (freezeTime > 0) {
            long passed = System.currentTimeMillis() - time;
            freezeTime = freezeTime - passed;
            if (freezeTime < 0)
                freezeTime = 0;
            env.ui.setFreeze(id, freezeTime);
            time = System.currentTimeMillis();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }
        }
    }
    /**
     * Penalize a player (freeze) and update the field isFreezed.
     * @post: isFreezed set to false and player is in freeze
     */

    public void penalty() {
        freezeTime(env.config.penaltyFreezeMillis);
        isFreezed = false;
    }
    /**
     * @return: the score of the player
     */
    public int score() {
        return score;
    }
    /**
     * @return: the field IsValidSet
     */
    public boolean getIsValidSet() {
        return this.isValidSet;
    }
    /**
     * set the field IsValidSet
     * @param input: boolean value of validation of the set
     */
    public void setIsValidSet(boolean input) {
        this.isValidSet = input;
    }
}
