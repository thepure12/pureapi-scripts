import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import org.pureapps.api.ui.plugins.ShowIf;

/** The Fisher's settings, edited from the Scripter panel's Settings button. */
@ConfigGroup("scriptfisher")
public interface FisherConfig extends Config {

    /** A fishing spot option. */
    enum Method {
        NET("Net"),
        SMALL_NET("Small Net"),
        BIG_NET("Big Net"),
        BAIT("Bait"),
        LURE("Lure"),
        CAGE("Cage"),
        HARPOON("Harpoon");

        public final String action;

        Method(String action) {
            this.action = action;
        }
    }

    /** What to do with the catch when the inventory is full. */
    enum WhenFull {
        DROP, BANK
    }

    @ConfigItem(position = 0, keyName = "method", name = "Method",
            description = "The fishing spot option to click")
    default Method method() {
        return Method.LURE;
    }

    @ConfigItem(position = 1, keyName = "spotName", name = "Spot name",
            description = "Only use spots with this name, e.g. Rod Fishing spot. Empty uses any spot with the method")
    default String spotName() {
        return "";
    }

    @ConfigItem(position = 2, keyName = "whenFull", name = "When full",
            description = "Drop the catch where you stand, or bank it at the closest bank and walk back")
    default WhenFull whenFull() {
        return WhenFull.DROP;
    }

    @ConfigItem(position = 3, keyName = "keep", name = "Keep items",
            description = "Comma-separated items that are never dropped or banked")
    default String keep() {
        return "Fly fishing rod, Feather, Fishing rod, Fishing bait, Small fishing net, Big fishing net, "
                + "Lobster pot, Harpoon, Coins";
    }

    @ShowIf(key = "whenFull", value = "DROP")
    @Range(min = 1, max = 28)
    @ConfigItem(position = 4, keyName = "dropsPerTick", name = "Drops per tick",
            description = "Items dropped each game tick while emptying the inventory")
    default int dropsPerTick() {
        return 4;
    }

    @Range(min = 1, max = 20)
    @ConfigItem(position = 5, keyName = "idleTicks", name = "Idle ticks",
            description = "Game ticks without fishing before a spot is clicked again")
    default int idleTicks() {
        return 2;
    }
}
