package dev.vinciguerra.adrsentinel.web.annotation.waybill;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.waybill.validator.FilenameValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Jakarta Bean Validation) progettata per blindare 
 * i nomi dei file ricevuti tramite payload REST.
 * <p><b>Contesto Architetturale (Boundary Security):</b></p>
 * Questa annotazione rappresenta il marker dichiarativo da apporre sui campi dei DTO 
 * (o direttamente sui parametri dei Controller) per attivare le protezioni di sicurezza. 
 * Delega l'effettiva esecuzione della validazione alla classe {@link FilenameValidator}, 
 * garantendo il principio di separazione delle responsabilità (Separation of Concerns).
 * <p><b>Sicurezza e Prevenzione Vulnerabilità:</b></p>
 * Presidia il sistema operativo contro attacchi di tipo Path Traversal, LFI (Local File Inclusion) 
 * e OS Command Injection, assicurando che la stringa fornita sia sintatticamente 
 * sicura prima che possa raggiungere i servizi di elaborazione PDF o il File System.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see FilenameValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { FilenameValidator.class })
public @interface ValidatorFilename {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: invalid filename format. Must be 5-255 safe characters with a 3-4 char extension.";
	/**
	 * Partiziona l'esecuzione del vincolo associandolo a specifici gruppi di validazione 
	 * (Validation Groups).
	 * @return l'array delle classi (gruppi) a cui appartiene questo vincolo (di default vuoto).
	 */
	Class<?>[] groups() default {};
	/**
	 * Consente all'architettura di sistema di allegare metadati informativi (Payload) 
	 * alla violazione del vincolo, utili per tracciare livelli di severità dell'errore (es. WARNING, FATAL).
	 * @return l'array delle classi di payload estese (di default vuoto).
	 */
	Class<? extends Payload>[] payload() default {};
}
