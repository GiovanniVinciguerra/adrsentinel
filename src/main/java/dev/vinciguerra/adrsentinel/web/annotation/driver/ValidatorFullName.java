package dev.vinciguerra.adrsentinel.web.annotation.driver;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.driver.validator.FullNameValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di vincolo (Constraint) personalizzata per la validazione di campi o parametri
 * che rappresentano un nome e cognome completo (Full Name).
 * <p>
 * Questa annotazione delega la complessa logica di validazione alla classe {@link FullNameValidator}.
 * Garantisce che la stringa annotata rispetti rigorosi criteri di formattazione e lunghezza,
 * impedendo l'inserimento di valori nulli, stringhe vuote, o caratteri non ammessi (come numeri 
 * o simboli speciali al di fuori di spazi, trattini e underscore).
 * <p>
 * Essendo annotata con {@code @Retention(RUNTIME)}, i metadati del vincolo sono disponibili 
 * durante l'esecuzione dell'applicazione, permettendo al framework di validazione (es. Hibernate Validator) 
 * di intercettare e processare il campo. Può essere applicata direttamente sulle variabili di istanza 
 * ({@code FIELD}) o sugli argomenti dei metodi ({@code PARAMETER}).
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { FullNameValidator.class })
public @interface ValidatorFullName {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: full_name must be between 4 and 255 characters (letters, spaces, hyphens, underscores only).";
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
