package info.cho.universalMixin.example;

import info.cho.universalMixin.api.At;
import info.cho.universalMixin.api.Inject;
import info.cho.universalMixin.api.Mixin;
import info.cho.universalMixin.api.Replace;
import info.cho.universalMixin.api.Shadow;

@Mixin(Player.class)
public final class PlayerMixin {
    @Shadow
    private int health;

    private PlayerMixin() {}

    @Replace(method = "title")
    public String title() {
        return "Modded player (health=" + health + ")";
    }

    @Inject(method = "damage", at = At.HEAD)
    public static void beforeDamage() {
        System.out.println("[Mixin HEAD] About to apply damage");
    }

    @Inject(method = "damage", at = At.RETURN)
    public static void afterDamage() {
        System.out.println("[Mixin RETURN] Damage finished");
    }
}
