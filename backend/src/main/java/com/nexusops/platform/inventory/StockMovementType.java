package com.nexusops.platform.inventory;

/**
 * Supported inventory stock movement types.
 * Positive types increment stock; negative types decrement stock.
 */
public enum StockMovementType {
    INITIAL(1, false),
    RECEIPT(1, true),
    ADJUSTMENT_IN(1, true),
    ADJUSTMENT_OUT(-1, true),
    RETURN_IN(1, true),
    ORDER_DEDUCTION(-1, false);

    private final int sign;
    private final boolean manualAllowed;

    StockMovementType(int sign, boolean manualAllowed) {
        this.sign = sign;
        this.manualAllowed = manualAllowed;
    }

    public int sign() {
        return sign;
    }

    public boolean isManualAllowed() {
        return manualAllowed;
    }
}

