package info.cho.universalMixin.example;

import info.cho.universalMixin.api.Mixin;
import info.cho.universalMixin.api.Replace;
import info.cho.universalMixin.api.Shadow;
import info.cho.universalMixin.api.WidenAccess;

@Mixin(target = "info.cho.universalMixin.example.Player$Stats")
@WidenAccess(type = true, fields = {"bonus"})
public final class StatsMixin {
    @Shadow
    private static int bonus;

    private StatsMixin() {}

    @Replace(method = "bonus")
    public static int bonus() {
        return bonus + 1;
    }
}
