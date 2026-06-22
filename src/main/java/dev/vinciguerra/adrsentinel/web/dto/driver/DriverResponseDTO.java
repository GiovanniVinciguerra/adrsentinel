package dev.vinciguerra.adrsentinel.web.dto.driver;

import java.util.Set;
import java.util.stream.Collectors;
import dev.vinciguerra.adrsentinel.db.driver.Driver;

public record DriverResponseDTO(String fullName, String taxCode, String phoneNumber, String license, String licenseExpireDate,
		String cqcExpireDate, Set<String> driverApprovals, Boolean active, Boolean inTrnasit, Boolean historicalData) {
	
	public static DriverResponseDTO fromEntity(Driver entity) {
		if(entity == null)
			return null;
		
		return new DriverResponseDTO(
			entity.getFullName(),
			entity.getTaxCode(),
			entity.getPhoneNumber(),
			entity.getLicense(),
			entity.getLicenseExpireDate().toString(),
			entity.getCqcExpireDate().toString(),
			entity.getDriverApprovals().stream()
				.map(approval -> approval.name())
				.collect(Collectors.toSet()),
			entity.isActive(),
			entity.isInTransit(),
			false
		);
	}
}
