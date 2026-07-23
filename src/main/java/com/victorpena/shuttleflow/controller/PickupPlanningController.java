package com.victorpena.shuttleflow.controller;

import java.time.LocalDate;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.victorpena.shuttleflow.service.PickupPlanningService;

@Controller
public class PickupPlanningController {
	
	private final PickupPlanningService pickupPlanningService;
	
	public PickupPlanningController(
			PickupPlanningService pickupPlanningService) {
		this.pickupPlanningService = pickupPlanningService;
	}
	
	@GetMapping("/")
	public String showDashboard(Model model) {
		LocalDate selectedDate = LocalDate.now();
		
		model.addAttribute("SelectedDate", selectedDate);
		model.addAttribute("pickupWindows", pickupPlanningService.getPickupWindowsForDate(selectedDate)
				);
		
		return "index";
	}
	

}
