import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import org.pureapps.api.scripts.Script;
import org.pureapps.api.scripts.ScriptManifest;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;

/**
 * Ranging Guild archery competition. Talks to the Competition Judge to pay for a round,
 * equips the bronze arrows it gives, and fires at the target until the round ends.
 * Wields the best bow in the inventory at the start and on each level up.
 * Stops at the goals set in the script's settings.
 */
@ScriptManifest(name = "Ranging Guild", version = "1.0", description = "Archery competition at the Ranging Guild")
public class Main extends Script {

    /** Shots left in the current round; 0 or less means a new round must be bought. */
    private static final int VARP_SHOTS = 156;
    private static final String JUDGE = "Competition Judge";

    @Inject
    private RangingGuildConfig config;
    @Inject
    private ItemManager itemManager;

    private int startXp;
    private int xpGained;
    private int startTick;
    private int rangedLevel;
    private int wait;

    @Override
    public void onStart() {
        startXp = api.skills.xp(Skill.RANGED);
        rangedLevel = api.skills.level(Skill.RANGED);
        startTick = client.getTickCount();
        equipBestBow();
        addOverlay(new StatsOverlay());
    }

    @Override
    public void onLoop() {
        if (config.timeStop() && ticks() * 0.6 >= config.time() * 60) {
            goalReached("time");
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (api.inventory.contains(ItemID.BRONZE_ARROW)) {
            status("Equipping arrows");
            if (api.inventory.equip(ItemID.BRONZE_ARROW)) wait = 1;
        } else if (client.getVarpValue(VARP_SHOTS) < 1) {
            status("Talking to the judge");
            if (api.dialogue.isOpen()) {
                api.dialogue.handle("Sure");
            } else if (api.npcs.talk(JUDGE)) {
                wait = 2;
            }
        } else {
            status("Firing at the target");
            if (api.objects.interact("Target", "Fire-at")) wait = 2;
        }
    }

    /** Wields the inventory bow with the best ranged attack that the Ranged level allows. */
    private void equipBestBow() {
        int equippedAttack = api.equipment.get(EquipmentInventorySlot.WEAPON)
                .map(item -> rangedAttack(item.getId())).orElse(Integer.MIN_VALUE);
        int bestId = -1;
        int bestAttack = equippedAttack;
        for (Widget item : api.inventory.items().list()) {
            int id = item.getItemId();
            if (Bows.level(id) > rangedLevel) continue;
            int attack = rangedAttack(id);
            if (attack > bestAttack) {
                bestId = id;
                bestAttack = attack;
            }
        }
        if (bestId >= 0) {
            log("Wielding {}", itemManager.getItemComposition(bestId).getName());
            api.inventory.equip(bestId);
        }
    }

    private int rangedAttack(int itemId) {
        ItemStats stats = itemManager.getItemStats(itemId);
        ItemEquipmentStats equipment = stats != null ? stats.getEquipment() : null;
        return equipment != null ? equipment.getArange() : Integer.MIN_VALUE;
    }

    private void goalReached(String goal) {
        log("Goal {} reached, stopping", goal);
        stop();
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        if (event.getSkill() != Skill.RANGED) return;
        xpGained = event.getXp() - startXp;
        if (event.getLevel() > rangedLevel) {
            rangedLevel = event.getLevel();
            if (config.levelStop() && rangedLevel >= config.level()) {
                goalReached("level");
            } else {
                equipBestBow();
            }
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() == InventoryID.INV && config.ticketStop()
                && api.inventory.quantity(ItemID.ARCHERY_TICKET) >= config.tickets()) {
            goalReached("tickets");
        }
    }

    private int ticks() {
        return client.getTickCount() - startTick;
    }

    private static String formatTime(int ticks) {
        int seconds = (int) (ticks * 0.6);
        int h = seconds / 3600, m = seconds % 3600 / 60, s = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    private int xpPerHour() {
        double hours = ticks() * 0.6 / 3600;
        return hours > 0 && xpGained > 0 ? (int) (xpGained / hours) : 0;
    }

    /** Run time, xp gained and xp per hour. */
    private class StatsOverlay extends OverlayPanel {
        StatsOverlay() {
            setPosition(OverlayPosition.TOP_LEFT);
        }

        @Override
        public Dimension render(Graphics2D graphics) {
            panelComponent.getChildren().add(LineComponent.builder().left("State:").right(getStatus()).build());
            panelComponent.getChildren().add(LineComponent.builder().left("Time running:").right(formatTime(ticks())).build());
            panelComponent.getChildren().add(LineComponent.builder().left("Xp gained:").right(String.valueOf(xpGained)).build());
            panelComponent.getChildren().add(LineComponent.builder().left("Xp/hr:").right(String.valueOf(xpPerHour())).build());
            return super.render(graphics);
        }
    }
}
