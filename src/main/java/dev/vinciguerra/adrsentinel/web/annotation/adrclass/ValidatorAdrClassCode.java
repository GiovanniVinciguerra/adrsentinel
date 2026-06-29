package dev.vinciguerra.adrsentinel.web.annotation.adrclass;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.adrclass.validator.AdrClassCodeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Constraint) progettata per blindare il perimetro 
 * delle API (Edge Validation). Verifica che una stringa rispetti rigorosamente la 
 * nomenclatura alfanumerica delle classi ADR (es. "3", "5.1", "6.1A").
 * <p><b>Contesto Architetturale (Anti-Corruption Layer):</b></p>
 * L'utilizzo di questa annotazione sui Data Transfer Object (Request Payload) o sui 
 * parametri dei Controller ({@code @RequestParam}, {@code @PathVariable}) garantisce che 
 * il Service Layer riceva esclusivamente dati sintatticamente validi, respingendo input 
 * malformati direttamente alla frontiera (HTTP 400).
 * La logica di ispezione basata su Regex è delegata alla classe {@link AdrClassCodeValidator}.
 * <p><b>Esempio d'uso (Composizione dei Vincoli):</b></p>
 * <pre>
 * {@code
 * @NotBlank(message = "ADR class code is strictly required.")
 * @ValidatorAdrClassCode
 * String adrClassCode;
 * }
 * </pre>
 * @see AdrClassCodeValidator
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({FIELD, PARAMETER, TYPE_USE})
@Constraint(validatedBy = { AdrClassCodeValidator.class })
public @interface ValidatorAdrClassCode {
	/**
	 * Definisce il messaggio di errore predefinito restituito al client (HTTP 400) 
	 * qualora il valore fornito non rispetti il formato ADR.
	 * @return la stringa contenente il messaggio di errore.
	 */
	String message() default "Malformed payload: the provided ADR class code is missing or invalid.";
	/**
	 * Permette di specificare i gruppi di validazione a cui appartiene questo vincolo.
	 * Utilizzato per applicare logiche di validazione condizionale (es. validare diversamente 
	 * in fase di Creazione rispetto alla fase di Aggiornamento).
	 * @return l'array dei gruppi di validazione (di default vuoto).
	 */
	Class<?>[] groups() default {};
	/**
	 * Permette all'architettura di sistema di assegnare payload personalizzati (metadati) 
	 * all'errore di validazione (es. livelli di severità come INFO, WARNING, FATAL).
	 * @return l'array delle classi di payload estese (di default vuoto).
	 */
	Class<? extends Payload>[] payload() default {};
}
