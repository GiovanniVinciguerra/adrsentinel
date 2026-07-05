package dev.vinciguerra.adrsentinel.db.customer;

import java.util.List;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import dev.vinciguerra.adrsentinel.db.AbstractGenericService;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;

/**
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Service
public class CustomerSnapshotService extends AbstractGenericService {
	private final CustomerSnapshotRepository customerSnapshotRepository;
	
	protected CustomerSnapshotService(CustomerSnapshotRepository customerSnapshotRepository, CacheManager cacheManager) {
		super(cacheManager);
		this.customerSnapshotRepository = customerSnapshotRepository;
	}
	
	public List<CustomerSnapshot> getByShipmentId(Long id) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Searching for the CustomerSnapshot by Shipment.Id: {}", id);
		return customerSnapshotRepository.findByShipment_Id(id);
	}
	
	@Transactional
	public CustomerSnapshot save(CustomerSnapshot newCustomerSnapshot) {
		logger.info("[DataBase CALL] Saving new CustomerSnapshot with vat number: {}", newCustomerSnapshot.getVatNumberSnap());
		CustomerSnapshot savedCustomerSnapshot = customerSnapshotRepository.save(newCustomerSnapshot);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterInsert(savedCustomerSnapshot); }
		});
		return savedCustomerSnapshot;
	}
	
	private void syncCacheAfterInsert(CustomerSnapshot savedCustomerSnapshot) {
		/* TODO Aggiornamento cache */
	}
}
