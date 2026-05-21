package dev.vinciguerra.adrsentinel.web.annotation.onunumber;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.validator.OnuNumberNameValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Constraint) progettata per il controllo perimetrale 
 * (Edge Validation) sulla denominazione tecnica ufficiale associata al Numero ONU (Proper Shipping Name).
 * Verifica simultaneamente la presenza obbligatoria del dato (Strict Validation) e i suoi vincoli dimensionali.
 * <p><b>Contesto Architetturale (Composite Validation & Domain-Driven Design):</b></p>
 * Questa interfaccia agisce come un validatore composito. Condensa in un'unica regola di 
 * business semantica le logiche infrastrutturali che tradizionalmente richiederebbero 
 * l'uso congiunto di più annotazioni standard (come {@code @NotBlank} e {@code @Size(min=3, max=255)}). 
 * Mantiene i Data Transfer Object (DTO) puliti, privi di rumore infrastrutturale, e garantisce 
 * che il Service Layer riceva esclusivamente denominazioni valide e pronte per la persistenza.
 * La logica di ispezione profonda è delegata alla classe {@link OnuNumberNameValidator}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see OnuNumberNameValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { OnuNumberNameValidator.class })
public @interface ValidatorOnuNumberName {
	/**
	 * Il messaggio di errore unificato che verrà restituito nel payload di risposta (es. HTTP 400) 
	 * in caso di fallimento di uno qualsiasi dei vincoli sottostanti.
	 * @return il messaggio testuale che descrive chiaramente sia l'obbligatorietà che il limite dimensionale.
	 */
	String message() default "Malformed payload: technical name is missing or invalid (expected 3-255 characters).";
	/**
	 * Partiziona l'esecuzione del vincolo associandolo a specifici Validation Groups.
	 * <p>Utile per differenziare i controlli a seconda del contesto (es. Creazione vs Aggiornamento).</p>
	 * In questo caso è lasciato volutamente vuoto.
	 * @return l'array delle classi (gruppi) a cui appartiene questo vincolo.
	 */
	Class<?>[] groups() default {};
	/**
	 * Consente di allegare metadati informativi (Payload) alla violazione del vincolo, 
	 * tipicamente utilizzati per definire il livello di severità dell'errore.
	 * <p>Volutamente lasciato vuoto in questo caso.</p>
	 * @return l'array delle classi payload associate.
	 */
	Class<? extends Payload>[] payload() default {};
}
