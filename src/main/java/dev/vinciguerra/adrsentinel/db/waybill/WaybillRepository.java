package dev.vinciguerra.adrsentinel.db.waybill;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WaybillRepository extends JpaRepository<Waybill, Long> {
	Optional<Waybill> findByShipment_Id(Long id);
	boolean existsByShipment_Id(Long id);
}
