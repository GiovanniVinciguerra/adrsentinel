package dev.vinciguerra.adrsentinel.db.customer;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import dev.vinciguerra.adrsentinel.db.AbstractGenericService;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;

@Service
public class CustomerService extends AbstractGenericService {
	private final CustomerRepository customerRepository;

	protected CustomerService(CustomerRepository customerRepository, CacheManager cacheManager) {
		super(cacheManager);
		this.customerRepository = customerRepository;
	}
	
	public Customer getByVatNumber(String vatNumber) {
		logger.info("[DataBase CALL] Searching for the Customer by vatNumber: {}", vatNumber);
		return customerRepository.findByVatNumber(vatNumber)
			.orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + vatNumber));
	}
}
