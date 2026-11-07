package example;

/** Application code needs no dependency on the mixin API. */
public final class Player {
    private int health = 20;

    public String title() {
        return "Original player";
    }
}
