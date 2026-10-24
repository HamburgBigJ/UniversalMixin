package info.cho.universalMixin.api;

public enum At {
    /** Before the first instruction of the target method. */
    HEAD,
    /** Before each normal return; not run when the target throws. */
    RETURN
}
