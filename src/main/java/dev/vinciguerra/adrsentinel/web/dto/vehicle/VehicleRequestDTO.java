package dev.vinciguerra.adrsentinel.web.dto.vehicle;

import dev.vinciguerra.adrsentinel.web.annotation.ValidatorRequiredNumber;
import dev.vinciguerra.adrsentinel.web.annotation.ValidatorRequiredString;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorLicensePlate;

public record VehicleRequestDTO(@ValidatorLicensePlate String licensePlate, @ValidatorRequiredString String vehicleType,
	@ValidatorRequiredString String loadType, @ValidatorRequiredNumber Integer maxWeightkg,
	@ValidatorRequiredNumber Integer maxUsefulWeightkg, @ValidatorRequiredNumber Integer heightcm,
	@ValidatorRequiredNumber Integer widthcm, @ValidatorRequiredNumber Integer lengthcm,
	@ValidatorRequiredNumber Integer wheelbasecm, @ValidatorRequiredNumber Integer nAxles, boolean adrCertified) {}
