import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import org.pureapps.api.ui.plugins.ShowIf;

/** The Ranging Guild script's settings: optional goals that stop the script. */
@ConfigGroup("scriptrangingguild")
public interface RangingGuildConfig extends Config {

    @ConfigItem(position = 0, keyName = "levelStop", name = "Stop at goal level",
            description = "Stop when Ranged reaches the goal level")
    default boolean levelStop() {
        return false;
    }

    @ShowIf(key = "levelStop", value = "true")
    @Range(min = 1, max = 99)
    @ConfigItem(position = 1, keyName = "level", name = "Goal level",
            description = "The Ranged level to stop at")
    default int level() {
        return 50;
    }

    @ConfigItem(position = 2, keyName = "timeStop", name = "Stop at goal minutes",
            description = "Stop after running for the goal minutes")
    default boolean timeStop() {
        return false;
    }

    @ShowIf(key = "timeStop", value = "true")
    @Range(min = 1)
    @ConfigItem(position = 3, keyName = "time", name = "Goal minutes",
            description = "Minutes to run for")
    default int time() {
        return 60;
    }

    @ConfigItem(position = 4, keyName = "ticketStop", name = "Stop at goal tickets",
            description = "Stop when the inventory holds the goal number of archery tickets")
    default boolean ticketStop() {
        return false;
    }

    @ShowIf(key = "ticketStop", value = "true")
    @Range(min = 1)
    @ConfigItem(position = 5, keyName = "tickets", name = "Goal tickets",
            description = "Archery tickets to stop at")
    default int tickets() {
        return 2000;
    }
}
