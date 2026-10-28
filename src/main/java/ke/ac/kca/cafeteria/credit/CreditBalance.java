package ke.ac.kca.cafeteria.credit;

import ke.ac.kca.cafeteria.menu.PriceParser;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Where a student's credit stands, worked out from the ledger. Held is the total of orders waiting for staff to
 * accept them; owed is what has been charged and not yet paid. Both count against the limit.
 */
public final class CreditBalance {

    private final boolean hasAccount;
    private final BigDecimal limit;
    private final BigDecimal held;
    private final BigDecimal owed;

    public CreditBalance(boolean hasAccount, BigDecimal limit, BigDecimal held, BigDecimal owed) {
        this.hasAccount = hasAccount;
        this.limit = limit;
        this.held = held.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        this.owed = owed.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isHasAccount() {
        return hasAccount;
    }

    public BigDecimal getLimit() {
        return limit;
    }

    public BigDecimal getHeld() {
        return held;
    }

    public BigDecimal getOwed() {
        return owed;
    }

    /** Held plus owed: everything counted against the limit. */
    public BigDecimal getUsed() {
        return held.add(owed);
    }

    /** What the student can still spend. Never below zero, even if the limit was lowered under what is used. */
    public BigDecimal getAvailable() {
        return limit.subtract(getUsed()).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isCanUse() {
        return hasAccount && getAvailable().signum() > 0;
    }

    public String getLimitText() {
        return PriceParser.format(limit);
    }

    public String getHeldText() {
        return PriceParser.format(held);
    }

    public String getOwedText() {
        return PriceParser.format(owed);
    }

    public String getAvailableText() {
        return PriceParser.format(getAvailable());
    }
}
