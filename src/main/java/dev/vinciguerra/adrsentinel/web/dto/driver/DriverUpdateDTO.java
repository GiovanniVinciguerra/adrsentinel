package dev.vinciguerra.adrsentinel.web.dto.driver;

import dev.vinciguerra.adrsentinel.web.annotation.ValidatorLocalDate;
import dev.vinciguerra.adrsentinel.web.annotation.driver.ValidatorFullName;

public record DriverUpdateDTO(@ValidatorFullName String fullName, String phoneNumber,
	@ValidatorLocalDate String licenseExpireDate, @ValidatorLocalDate String cqcExpireDate) {}
