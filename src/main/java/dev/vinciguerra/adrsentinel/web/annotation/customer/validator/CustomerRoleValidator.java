package dev.vinciguerra.adrsentinel.web.annotation.customer.validator;

import dev.vinciguerra.adrsentinel.db.customer.Customer.CustomerRole;
import dev.vinciguerra.adrsentinel.web.annotation.customer.ValidatorCustomerRole;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CustomerRoleValidator implements ConstraintValidator<ValidatorCustomerRole, String> {
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null || value.isBlank())
			return false;
		try {
			Enum.valueOf(CustomerRole.class, value);
		} catch(IllegalArgumentException error) {
			return false;
		}
		return value.length() <= 255;
	}
}
