package dev.vinciguerra.adrsentinel.web.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle;
import dev.vinciguerra.adrsentinel.db.vehicle.VehicleService;
import dev.vinciguerra.adrsentinel.web.annotation.ValidatorRequiredNumber;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorLicensePlate;
import dev.vinciguerra.adrsentinel.web.dto.vehicle.VehicleRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.vehicle.VehicleResponseDTO;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/adr-sentinel/vehicles")
@Validated
public class VehicleController {
	private final VehicleService vehicleService;
	
	public VehicleController(VehicleService vehicleService) {
		this.vehicleService = vehicleService;
	}
	
	@GetMapping
	public ResponseEntity<List<VehicleResponseDTO>> getAllVehicle() {
		List<Vehicle> vehicles = vehicleService.getAllVehicle();
		List<VehicleResponseDTO> response = vehicles.stream().map(VehicleResponseDTO::fromEntity).toList();
		return ResponseEntity.ok(response);
	}
	
	@GetMapping("/{licensePlate}")
	public ResponseEntity<VehicleResponseDTO> getByLicensePlate(@PathVariable @ValidatorLicensePlate String licensePlate) {
		Vehicle vehicle = vehicleService.getByLicensePlate(licensePlate);
		return ResponseEntity.ok(VehicleResponseDTO.fromEntity(vehicle));
	}
	
	@GetMapping("/weight/{maxUsefulWeight}")
	public ResponseEntity<List<VehicleResponseDTO>> getByMaxUsefulWeight(@PathVariable @ValidatorRequiredNumber Integer maxUsefulWeight) {
		List<Vehicle> vehicles = vehicleService.getByMaxUsefulWeight(maxUsefulWeight);
		List<VehicleResponseDTO> response = vehicles.stream().map(VehicleResponseDTO::fromEntity).toList();
		return ResponseEntity.ok(response);
	}
	
	@PostMapping
	public ResponseEntity<VehicleResponseDTO> create(@RequestBody @Valid VehicleRequestDTO vehicleRequestDTO) {
		Vehicle vehicleToSave = vehicleService.mapToEntity(vehicleRequestDTO);
		Vehicle savedVehicle = vehicleService.save(vehicleToSave);
		return ResponseEntity.status(HttpStatus.CREATED).body(VehicleResponseDTO.fromEntity(savedVehicle));
	}
}
