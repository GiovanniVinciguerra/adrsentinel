package dev.vinciguerra.adrsentinel.db.waybill;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.vinciguerra.adrsentinel.db.AbstractGenericService;
import dev.vinciguerra.adrsentinel.exception.IllegalShipmentStateException;

@Service
public class WaybillService extends AbstractGenericService  {
	private final WaybillRepository waybillRepository;

	protected WaybillService(WaybillRepository waybillRepository, CacheManager cacheManager) {
		super(cacheManager);
		this.waybillRepository = waybillRepository;
	}
	
	@Transactional(readOnly = true)
	public Waybill getWaybillByShipmentId(Long id) {
		logger.info("[DataBase CALL] Searching for the Waybill by shipment id: {}", id);
		return waybillRepository.findByShipment_Id(id)
			.orElseThrow(() -> new RuntimeException("Waybill not found for shipment ID:: " + id));
	}
	
	@Transactional(readOnly = true)
	public boolean isPresentByShipment_Id(Long id) {
		logger.info("[DataBase CALL] Checking existence of Waybill for Shipment ID: {}", id);
		return waybillRepository.existsByShipment_Id(id);
	}
	
	@Transactional
	public Waybill save(Waybill newWaybill) throws IllegalShipmentStateException {
		logger.info("[DataBase CALL] Saving new Waybill with filename: {}", newWaybill.getFilename());
		boolean isPresent = waybillRepository.existsByShipment_Id(newWaybill.getShipment().getId());
		if(isPresent)
			throw new IllegalShipmentStateException("A waybill already exists for shipment tracking number: " + newWaybill.getShipment().getTrackingNumber());
		Waybill savedWaybill = waybillRepository.save(newWaybill);
		return savedWaybill;
	}
}
