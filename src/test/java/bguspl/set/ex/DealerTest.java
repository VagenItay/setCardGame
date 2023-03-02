package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DealerTest {

    Player player;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Dealer dealer;
    @Mock
    private Logger logger;
    
    void assertInvariants() {
        assertTrue(player.id >= 0);
        assertTrue(player.score() >= 0);
    }

    @BeforeEach
    void setUp() {
        // purposely do not find the configuration files (use defaults here).
        Env env = new Env(logger, new Config(logger, (String) null), ui, util);
        Integer[] newSlotToCard=new Integer[12];
        Integer[] newCardToslot=new Integer[81];
        Player[] players = {};
        table= new Table(env,newSlotToCard,newCardToslot);
        dealer=new Dealer(env, table, players);
        player = new Player(env, dealer, table, 0, false);
        
        assertInvariants();
    }

    @AfterEach
    void tearDown() {
        assertInvariants();
    }

    /**
     * place cards on table and then remove them all.
     * @pre - slots are empty and deck size is 81. 
     * @post - all slots are still empty and deck size is 81
     */
    @Test
     void placeandRemoveTokens() {
        // call the method we are testing
        dealer.placeCardsOnTable();
        assertEquals(69, dealer.getDeck().size());//took 12 cards from table
        dealer.removeAllCardsFromTable();
        assertEquals(81, dealer.getDeck().size());//return 12 cards to deck
    } 
     /**
     * update reshuffle time.
     * @pre - reshuffle time is equal to Long.MAX_VALUE. 
     * @post - reshuffle time is changed(with updateTimerDisplay to System.currentTimeMillis() +
             env.config.turnTimeoutMillis)
     */
    @Test
    void Timer() {
        long expectedReshfulleTime = dealer.getReShuffle();
        dealer.updateTimerDisplay(true);
        boolean check = false;
        boolean secondCheck= dealer.getReShuffle()==expectedReshfulleTime;
        // check that the reshffule time has changed
        assertEquals(check, secondCheck);
    }
}
