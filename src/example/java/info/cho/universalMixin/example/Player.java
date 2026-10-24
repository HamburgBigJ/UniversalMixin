package info.cho.universalMixin.example;

/** An ordinary application class with no knowledge of the mixin library. */
public final class Player {
    private int health = 20;

    private static final class Stats {
        private static int bonus = 5;

        public static int bonus() {
            return bonus;
        }
    }

    public int bonus() {
        return Stats.bonus();
    }

    public String title() {
        return "Player";
    }

    public int damage(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Damage must not be negative");
        }
        health = Math.max(0, health - amount);
        System.out.println("[Player] Took " + amount + " damage; health = " + health);
        return health;
    }
}
