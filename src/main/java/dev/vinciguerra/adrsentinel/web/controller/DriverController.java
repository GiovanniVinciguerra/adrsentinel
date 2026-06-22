package dev.vinciguerra.adrsentinel.web.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.vinciguerra.adrsentinel.db.driver.Driver;
import dev.vinciguerra.adrsentinel.db.driver.DriverService;
import dev.vinciguerra.adrsentinel.web.annotation.driver.ValidatorLicense;
import dev.vinciguerra.adrsentinel.web.dto.driver.DriverResponseDTO;

@RestController
@RequestMapping("/adr-sentinel/drivers")
@Validated
public class DriverController {
	private final DriverService driverService;
	
	public DriverController(DriverService driverService) {
		this.driverService = driverService;
	}
	
	@GetMapping
	public ResponseEntity<List<DriverResponseDTO>> getAllDriver() {
		List<Driver> drivers = driverService.getAllDriver();
		List<DriverResponseDTO> response = drivers.stream().map(DriverResponseDTO::fromEntity).toList();
		return ResponseEntity.ok(response);
	}
	
	@GetMapping("/{license}")
	public ResponseEntity<DriverResponseDTO> getByLicense(@PathVariable @ValidatorLicense String license) {
		Driver driver = driverService.getByLicense(license);
		return ResponseEntity.ok(DriverResponseDTO.fromEntity(driver));
	}
}
