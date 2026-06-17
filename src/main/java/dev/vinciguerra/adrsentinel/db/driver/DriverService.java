package dev.vinciguerra.adrsentinel.db.driver;

import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import dev.vinciguerra.adrsentinel.db.AbstractGenericService;
import dev.vinciguerra.adrsentinel.db.CaffeineCacheConfiguration;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;

public class DriverService extends AbstractGenericService {
	private final DriverRepository driverRepository;
	
	protected DriverService(DriverRepository driverRepository, CacheManager cacheManager) {
		super(cacheManager);
		this.driverRepository = driverRepository;
	}
	
	@Cacheable(value = CaffeineCacheConfiguration.DRIVER_BY_LICENSE_CACHE, key = "#license")
	public Driver getByLicense(String license) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Searching for the Driver by license: {}", license);
		return driverRepository.findByLicense(license)
			.orElseThrow(() -> new ResourceNotFoundException("Driver not found: " + license));
	}
	
	@Cacheable(value = CaffeineCacheConfiguration.ALL_DRIVER_CACHE, key = "'" + CaffeineCacheConfiguration.ALL_DRIVER_KEY + "'")
	public List<Driver> getAllDriver() {
		logger.info("[DataBase CALL] Retrieving all Driver");
		return driverRepository.findAll();
	}
	
	@Transactional
	public Driver save(Driver newDriver) {
		logger.info("[DataBase CALL] Saving new Driver with license: {}", newDriver.getLicense());
		Driver savedDriver = driverRepository.save(newDriver);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterInsertOrUpdate(savedDriver); }
		});
		return savedDriver;
	}
	
	private void syncCacheAfterInsertOrUpdate(Driver savedDriver) {
		storeInCache(
			CaffeineCacheConfiguration.DRIVER_BY_LICENSE_CACHE,
			savedDriver.getLicense(),
			savedDriver,
			CacheOperation.SINGLE_RECORD
		);
		storeInCache(
			CaffeineCacheConfiguration.ALL_DRIVER_CACHE,
			CaffeineCacheConfiguration.ALL_DRIVER_KEY,
			savedDriver,
			CacheOperation.LIST_RECORD
		);
	}
}
