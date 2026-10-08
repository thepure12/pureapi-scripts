import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import org.pureapps.api.Walker;
import org.pureapps.api.query.NpcQuery;
import org.pureapps.api.scripts.Script;
import org.pureapps.api.scripts.ScriptManifest;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Simple fisher. Clicks the nearest reachable fishing spot with the configured method and waits
 * while the player fishes. When the inventory is full it either drops everything except the kept
 * items, or banks them at the closest bank and walks back to where the script started.
 * Change the settings with the Scripter panel's Settings button; they apply while it runs.
 */
@ScriptManifest(name = "Fisher", version = "1.2", description = "Fishes at the nearest spot, drops or banks")
public class Main extends Script {

    private enum Phase { FISH, DROP, BANK, RETURN }

    /** How close to the start tile counts as back at the spots. */
    private static final int HOME_RADIUS = 10;
    /** Ticks to wait for the bank trip before asking for it again. */
    private static final int BANK_RETRY_TICKS = 30;

    @Inject
    private FisherConfig config;

    private Phase phase = Phase.FISH;
    private WorldPoint home;
    private int idle;
    private int wait;
    /** Ticks until the deposit is asked for again. */
    private int bankRetry;
    private int caught;
    private int banked;
    private int lastFree = -1;

    @Override
    public void onStart() {
        home = api.player.getWorldLocation();
        log("Fishing with '{}'{}, {} when full", config.method().action,
                config.spotName().isEmpty() ? "" : " at " + config.spotName(), config.whenFull());
    }

    // Runs on the client thread once per game tick: don't sleep here.
    @Override
    public void onLoop() {
        countCatch();
        if (wait > 0) {
            wait--;
            return;
        }
        switch (phase) {
            case FISH:
                fish();
                break;
            case DROP:
                drop();
                break;
            case BANK:
                bank();
                break;
            case RETURN:
                walkBack();
                break;
        }
    }

    private void fish() {
        if (api.inventory.isFull()) {
            if (junk().isEmpty()) {
                log("Inventory is full of kept items, stopping");
                stop();
                return;
            }
            phase = config.whenFull() == FisherConfig.WhenFull.BANK ? Phase.BANK : Phase.DROP;
            bankRetry = 0;
            return;
        }

        if (api.player.isAnimating() || api.player.isMoving()) {
            idle = 0;
            status("Fishing (" + caught + " caught)");
            return;
        }
        if (++idle < config.idleTicks()) return;

        String action = config.method().action;
        NpcQuery spots = api.npcs.search().withAction(action);
        if (!config.spotName().isEmpty()) spots = spots.withName(config.spotName());
        Optional<NPC> spot = spots.nearestReachable();
        if (!spot.isPresent()) {
            status("No spot with '" + action + "'");
            return;
        }
        if (api.npcs.interact(spot.get(), action)) {
            status("Clicking spot");
            idle = 0;
            wait = 3;
        }
    }

    private void drop() {
        List<Widget> junk = junk();
        if (junk.isEmpty()) {
            phase = Phase.FISH;
            return;
        }
        status("Dropping (" + junk.size() + " left)");
        for (Widget item : junk.subList(0, Math.min(config.dropsPerTick(), junk.size()))) {
            api.inventory.interact(item, "Drop");
        }
    }

    /**
     * Asks the bank to deposit everything but the kept items. While the bank is closed,
     * {@code depositAllExcept} walks to the closest bank and opens it first.
     */
    private void bank() {
        List<Widget> junk = junk();
        if (junk.isEmpty()) {
            api.bank.close();
            log("Banked, {} trips", ++banked);
            phase = Phase.RETURN;
            return;
        }
        status(api.bank.isOpen() ? "Depositing" : "Walking to the bank");
        if (stuck()) return;
        if (bankRetry > 0) {
            bankRetry--;
            return;
        }
        api.bank.depositAllExcept(keep().toArray(new String[0]));
        bankRetry = api.bank.isOpen() ? 2 : BANK_RETRY_TICKS;
    }

    private void walkBack() {
        if (api.player.getWorldLocation().distanceTo(home) <= HOME_RADIUS) {
            api.walker.cancel();
            phase = Phase.FISH;
            idle = config.idleTicks();
            return;
        }
        status("Walking back");
        if (!api.walker.isWalking()) {
            if (stuck()) return;
            api.walker.walkTo(home);
            wait = 2;
        }
    }

    /** Stops the script when the walker can't get where it's going. */
    private boolean stuck() {
        Walker.WalkState state = api.walker.getState();
        if (state != Walker.WalkState.NO_PATH && state != Walker.WalkState.STUCK) return false;
        log("Walker is {}, stopping", state);
        stop();
        return true;
    }

    /** Inventory items that aren't kept. */
    private List<Widget> junk() {
        List<String> keep = keep();
        return api.inventory.items().filter(w -> {
            String name = client.getItemDefinition(w.getItemId()).getName();
            return keep.stream().noneMatch(name::equalsIgnoreCase);
        }).list();
    }

    private List<String> keep() {
        return Arrays.stream(config.keep().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    /** Counts a catch whenever the free slots shrink while fishing. */
    private void countCatch() {
        int free = api.inventory.emptySlots();
        if (phase == Phase.FISH && lastFree >= 0 && free < lastFree) caught += lastFree - free;
        lastFree = free;
    }

    @Override
    public void onStop() {
        api.walker.cancel();
        log("Caught {} fish, banked {} times", caught, banked);
    }
}
