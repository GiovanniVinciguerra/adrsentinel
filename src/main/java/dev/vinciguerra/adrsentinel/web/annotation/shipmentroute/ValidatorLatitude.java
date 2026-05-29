package dev.vinciguerra.adrsentinel.web.annotation.shipmentroute;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentroute.validator.LatitudeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Vincolo di validazione personalizzato (Custom Constraint) per le coordinate geografiche.
 * <p>
 * Assicura che il valore numerico ({@code Double}) annotato rappresenti una <b>latitudine 
 * geograficamente valida</b>. Matematicamente e cartograficamente, la latitudine rappresenta 
 * l'angolo misurato a partire dall'equatore e deve essere rigorosamente compreso nell'intervallo 
 * chiuso <b>[-90.0, +90.0]</b>.
 * </p>
 * <p>
 * <b>Scelte Architetturali (JSR-380):</b><br>
 * In conformità alle specifiche di Java Bean Validation, questo vincolo è tollerante all'assenza di dati: 
 * <b>considera validi i valori {@code null}</b>. La responsabilità di bloccare payload privi del campo 
 * deve essere delegata in modo ortogonale all'annotazione standard {@link jakarta.validation.constraints.NotNull}, 
 * evitando così la proliferazione di messaggi di errore duplicati nel layer REST.
 * Il validatore delegato ({@link LatitudeValidator}) funge inoltre da scudo di sicurezza contro 
 * anomalie in virgola mobile ({@code NaN}, {@code Infinity}).
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see LatitudeValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { LatitudeValidator.class })
public @interface ValidatorLatitude {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: latitude is required and must be a valid number between -90.0 and 90.0.";
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
