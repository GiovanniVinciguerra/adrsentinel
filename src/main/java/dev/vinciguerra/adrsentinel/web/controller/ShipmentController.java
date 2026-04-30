package dev.vinciguerra.adrsentinel.web.controller;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;
import dev.vinciguerra.adrsentinel.db.shipment.ShipmentService;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment.ShipmentStatus;
import dev.vinciguerra.adrsentinel.web.annotation.shipment.ValidatorLocalDate;
import dev.vinciguerra.adrsentinel.web.annotation.shipment.ValidatorShipmentStatus;
import dev.vinciguerra.adrsentinel.web.annotation.shipment.ValidatorTrackingNumber;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorLicensePlate;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentResponseDTO;

@RestController
@RequestMapping("/adr-sentinel/shipments")
@Validated
public class ShipmentController {
	private final ShipmentService shipmentService;
	
	public ShipmentController(ShipmentService shipmentService) {
		this.shipmentService = shipmentService;
	}
	
	@GetMapping
	public ResponseEntity<Page<ShipmentResponseDTO>> getAll(Pageable pageable) {
		Page<Shipment> page = shipmentService.getAllShipment(pageable);
		Page<ShipmentResponseDTO> response = page.map(this::mapToDTO);
		return ResponseEntity.ok(response);
	}
	
	@GetMapping("/status/{status}")
	public ResponseEntity<Page<ShipmentResponseDTO>> getByStatus(@PathVariable @ValidatorShipmentStatus String status, Pageable pageable) {
		Page<Shipment> page = shipmentService.getByShipmentStatus(Enum.valueOf(ShipmentStatus.class, status), pageable);
		Page<ShipmentResponseDTO> response = page.map(this::mapToDTO);
		return ResponseEntity.ok(response);
	}
	
	@GetMapping("/vehicle/{licensePlate}")
	public ResponseEntity<Page<ShipmentResponseDTO>> getByVehicle(@PathVariable @ValidatorLicensePlate String licensePlate, Pageable pageable) {
		Page<Shipment> page = shipmentService.getByVehicle(licensePlate, pageable);
		Page<ShipmentResponseDTO> response = page.map(this::mapToDTO);
		return ResponseEntity.ok(response);
	}
	
	@GetMapping("/{trackingNumber}")
	public ResponseEntity<ShipmentResponseDTO> getByTrackingNumber(@PathVariable @ValidatorTrackingNumber String trackingNumber) {
		Shipment shipment = shipmentService.getByTrackingNumber(trackingNumber);
		return ResponseEntity.ok(mapToDTO(shipment));
	}
	
	@GetMapping("/date/{date}")
	public ResponseEntity<List<ShipmentResponseDTO>> getByShipmentDate(@PathVariable @ValidatorLocalDate String date) {
		LocalDate parsedDate = LocalDate.parse(date);
		List<Shipment> shipments = shipmentService.getByShipmentDate(parsedDate);
		List<ShipmentResponseDTO> response = shipments.stream().map(this::mapToDTO).toList();
		return ResponseEntity.ok(response);
	}
	
	private ShipmentResponseDTO mapToDTO(Shipment entity) {
		return new ShipmentResponseDTO(
			entity.getId(),
			entity.getTrackingNumber(),
			entity.getShipmentDate(),
			entity.getShipmentStatus(),
			entity.getOriginAddress(),
			entity.getDestinationAddress(),
			entity.getDistancekm(),
			entity.getVehicle()
		);
	}
}
