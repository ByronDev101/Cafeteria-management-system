package ke.ac.kca.cafeteria.reporting;

import java.util.List;

/** Everything the manager reports page shows: the range, warnings, the totals and the search results. */
public final class ManagerReport {

    private final String fromText;
    private final String toText;
    private final List<String> warnings;
    private final long orderCount;
    private final List<ReportLine> orderLines;
    private final List<ReportLine> paymentLines;
    private final List<ReportLine> methodLines;
    private final String status;
    private final String numberText;
    private final String tableText;
    private final List<ReportRow> rows;
    private final boolean moreMatches;
    private final int limit;

    public ManagerReport(String fromText, String toText, List<String> warnings, long orderCount,
                         List<ReportLine> orderLines, List<ReportLine> paymentLines, List<ReportLine> methodLines,
                         String status, String numberText, String tableText, List<ReportRow> rows,
                         boolean moreMatches, int limit) {
        this.fromText = fromText;
        this.toText = toText;
        this.warnings = List.copyOf(warnings);
        this.orderCount = orderCount;
        this.orderLines = List.copyOf(orderLines);
        this.paymentLines = List.copyOf(paymentLines);
        this.methodLines = List.copyOf(methodLines);
        this.status = status;
        this.numberText = numberText;
        this.tableText = tableText;
        this.rows = List.copyOf(rows);
        this.moreMatches = moreMatches;
        this.limit = limit;
    }

    public String getFromText() {
        return fromText;
    }

    public String getToText() {
        return toText;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    /** All orders placed in the range, whatever their status. */
    public long getOrderCount() {
        return orderCount;
    }

    /** One line per order status: how many orders and their combined value. */
    public List<ReportLine> getOrderLines() {
        return orderLines;
    }

    /** One line per payment status: how many payment records and their combined amount. */
    public List<ReportLine> getPaymentLines() {
        return paymentLines;
    }

    /** Payments received, split by how they were paid. */
    public List<ReportLine> getMethodLines() {
        return methodLines;
    }

    /** The status filter in use, as a status code, or empty for all. */
    public String getStatus() {
        return status;
    }

    public String getNumberText() {
        return numberText;
    }

    public String getTableText() {
        return tableText;
    }

    public List<ReportRow> getRows() {
        return rows;
    }

    /** True when more orders matched than are listed. */
    public boolean isMoreMatches() {
        return moreMatches;
    }

    public int getLimit() {
        return limit;
    }
}
