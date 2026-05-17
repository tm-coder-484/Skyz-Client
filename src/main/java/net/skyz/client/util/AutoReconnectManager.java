package net.skyz.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

public class AutoReconnectManager {

    public static final AutoReconnectManager INSTANCE = new AutoReconnectManager();

    public enum State { IDLE, COUNTING_DOWN, FAILED }

    private static final int MAX_ATTEMPTS    = 5;
    private static final int COUNTDOWN_TICKS = 200; // 10 seconds

    private State      state           = State.IDLE;
    private ServerInfo lastServerInfo  = null;
    private int        reconnectAttempt = 0;
    private int        ticksRemaining   = 0;

    /** Called on successful server join — resets attempt counter and stores server info. */
    public void onJoin(ServerInfo info) {
        this.lastServerInfo   = info;
        this.reconnectAttempt = 0;
        this.state            = State.IDLE;
    }

    /**
     * Called by SkyzDisconnectedScreen when it opens. Starts the countdown if a
     * known server is stored and we haven't exhausted all attempts.
     */
    public void start() {
        if (lastServerInfo == null) return;
        if (reconnectAttempt >= MAX_ATTEMPTS) {
            state = State.FAILED;
            return;
        }
        reconnectAttempt++;
        ticksRemaining = COUNTDOWN_TICKS;
        state          = State.COUNTING_DOWN;
    }

    /** Called every client tick from SkyzClientMod. Fires reconnect when countdown hits zero. */
    public void tick(MinecraftClient client) {
        if (state != State.COUNTING_DOWN) return;
        if (--ticksRemaining <= 0) {
            state = State.IDLE; // prevents re-firing while ConnectScreen loads
            ConnectScreen.connect(new TitleScreen(), client,
                    ServerAddress.parse(lastServerInfo.address), lastServerInfo, false, null);
        }
    }

    /** Stops auto-reconnect and clears stored server info. */
    public void cancel() {
        state            = State.IDLE;
        lastServerInfo   = null;
        reconnectAttempt = 0;
    }

    public State getState()          { return state; }
    public int   getAttemptNumber()  { return reconnectAttempt; }
    public int   getMaxAttempts()    { return MAX_ATTEMPTS; }
    public int   getSecondsRemaining() {
        return Math.max(1, (ticksRemaining + 19) / 20);
    }
}
