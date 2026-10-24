package info.cho.universalMixin.example;

public final class ExampleMain {
    private ExampleMain() {}

    public static void main(String[] args) {
        Player player = new Player();
        int remainingHealth = player.damage(7);
        System.out.println("[App] Remaining health: " + remainingHealth);
        System.out.println("[App] Title: " + player.title());
        System.out.println("[App] Bonus: " + player.bonus());
    }
}
