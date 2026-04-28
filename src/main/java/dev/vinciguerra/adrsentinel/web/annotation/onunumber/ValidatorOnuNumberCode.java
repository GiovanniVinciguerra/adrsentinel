package dev.vinciguerra.adrsentinel.web.annotation.onunumber;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = {})
@NotNull(message = "Onu number code cannot be null")
@Pattern(
	regexp = "^\\d{4}$",
	message = "Invalid ONU code format: must be exactly 4 digits"
)
public @interface ValidatorOnuNumberCode {
	String message() default "Invalid Onu number code format or missing code.";
	Class<?>[] groups() default {};
	Class<? extends Payload>[] payload() default {};
}
