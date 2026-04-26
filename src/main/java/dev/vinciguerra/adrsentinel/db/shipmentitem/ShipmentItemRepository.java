package dev.vinciguerra.adrsentinel.db.shipmentitem;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;

@Repository
public interface ShipmentItemRepository extends JpaRepository<ShipmentItem, Long> {
	Optional<ShipmentItem> findByItemUUID(String itemUUID);
	List<ShipmentItem> findByShipment(Shipment shipment);
}
