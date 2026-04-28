package dev.vinciguerra.adrsentinel.web.annotation.onunumber;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;

@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Pattern(
	regexp = "^(X?\\d{2,3})$",
	message = "Invalid Kemler code format. Must be 2 or 3 digits, optionally prefixed by 'X'"
)
public @interface ValidatorKemlerCode {
	String message() default "Invalid Kemler code format. Must be 2 or 3 digits, optionally prefixed by 'X'";
	Class<?>[] groups() default {};
	Class<? extends Payload>[] payload() default {};
}
