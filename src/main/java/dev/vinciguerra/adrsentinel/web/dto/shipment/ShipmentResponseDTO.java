package dev.vinciguerra.adrsentinel.web.dto.shipment;

import java.time.LocalDateTime;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment.ShipmentStatus;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle;

public record ShipmentResponseDTO(Long id, String trackingNumber, LocalDateTime shipmentDate, ShipmentStatus shipmentStatus,
	String originAddress, String destinationAddress, Float distancekm, Vehicle vehicle) {}
