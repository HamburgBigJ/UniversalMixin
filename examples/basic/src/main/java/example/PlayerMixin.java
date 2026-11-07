package example;

import info.cho.universalMixin.api.Mixin;
import info.cho.universalMixin.api.Replace;
import info.cho.universalMixin.api.Shadow;

@Mixin(Player.class)
public final class PlayerMixin {
    @Shadow
    private int health;

    @Replace(method = "title")
    public String title() {
        return "Modded player: health=" + health;
    }
}
