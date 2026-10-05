package in.ggv.campora.admin.web;

import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import in.ggv.campora.admin.service.AdminService;
import in.ggv.campora.campus.domain.LocationType;
import in.ggv.campora.campus.service.CampusService;
import in.ggv.campora.campus.service.DestinationRequestService;
import in.ggv.campora.common.web.Flash;
import in.ggv.campora.web.form.DepartmentForm;
import in.ggv.campora.web.form.LocationForm;
import jakarta.validation.Valid;

/** Transport-office console. */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService admin;
    private final CampusService campus;
    private final DestinationRequestService proposals;

    public AdminController(AdminService admin, CampusService campus, DestinationRequestService proposals) {
        this.admin = admin;
        this.campus = campus;
        this.proposals = proposals;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("stats", admin.stats());
        model.addAttribute("drivers", admin.drivers());
        model.addAttribute("suggestions", proposals.pending());
        model.addAttribute("locations", campus.allLocations());
        model.addAttribute("departments", campus.allDepartments());
        model.addAttribute("locationTypes", LocationType.values());
        model.addAttribute("locationForm", new LocationForm());
        model.addAttribute("departmentForm", new DepartmentForm());
        return "admin/dashboard";
    }

    @PostMapping("/drivers/{id}/verify")
    public String verify(@PathVariable UUID id, RedirectAttributes attrs) {
        admin.verifyDriver(id);
        Flash.success(attrs, "Driver verified.");
        return "redirect:/admin#drivers";
    }

    @PostMapping("/drivers/{id}/revoke")
    public String revoke(@PathVariable UUID id, RedirectAttributes attrs) {
        admin.revokeDriver(id);
        Flash.success(attrs, "Verification revoked; driver set offline.");
        return "redirect:/admin#drivers";
    }

    @PostMapping("/suggestions/{id}/approve")
    public String approve(@PathVariable UUID id, @RequestParam("type") LocationType type, RedirectAttributes attrs) {
        proposals.approve(id, type);
        Flash.success(attrs, "Suggestion approved.");
        return "redirect:/admin#suggestions";
    }

    @PostMapping("/suggestions/{id}/reject")
    public String reject(@PathVariable UUID id, @RequestParam(name = "note", required = false) String note,
                         RedirectAttributes attrs) {
        proposals.reject(id, note);
        Flash.success(attrs, "Suggestion rejected.");
        return "redirect:/admin#suggestions";
    }

    @PostMapping("/locations")
    public String addLocation(@Valid @ModelAttribute("locationForm") LocationForm form, BindingResult result,
                              RedirectAttributes attrs) {
        if (result.hasErrors()) {
            Flash.error(attrs, Flash.firstError(result));
        } else {
            campus.addLocation(form.getName(), form.getArea(), form.getType());
            Flash.success(attrs, "Stop \"" + form.getName().trim() + "\" added.");
        }
        return "redirect:/admin#network";
    }

    @PostMapping("/locations/{id}/toggle")
    public String toggleLocation(@PathVariable UUID id, RedirectAttributes attrs) {
        campus.toggleLocation(id);
        Flash.success(attrs, "Stop updated.");
        return "redirect:/admin#network";
    }

    @PostMapping("/departments")
    public String addDepartment(@Valid @ModelAttribute("departmentForm") DepartmentForm form, BindingResult result,
                                RedirectAttributes attrs) {
        if (result.hasErrors()) {
            Flash.error(attrs, Flash.firstError(result));
        } else {
            campus.addDepartment(form.getCode(), form.getName());
            Flash.success(attrs, "Department added.");
        }
        return "redirect:/admin#departments";
    }

    @PostMapping("/departments/{id}/toggle")
    public String toggleDepartment(@PathVariable UUID id, RedirectAttributes attrs) {
        campus.toggleDepartment(id);
        Flash.success(attrs, "Department updated.");
        return "redirect:/admin#departments";
    }
}
