package ke.ac.kca.cafeteria.reporting;

import ke.ac.kca.cafeteria.ordering.OrderStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The manager reports page (FR-033 to FR-035). Managers only; see SecurityConfig and ReportService. */
@Controller
public class ManagerReportController {

    private final ReportService reports;

    public ManagerReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/manager/reports")
    public String reports(@RequestParam(required = false) String from,
                          @RequestParam(required = false) String to,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) String number,
                          @RequestParam(required = false) String table,
                          Model model) {
        model.addAttribute("report", reports.build(from, to, status, number, table));
        model.addAttribute("statuses", OrderStatus.values());
        return "manager/reports";
    }
}
