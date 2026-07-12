package dev.vinciguerra.adrsentinel.web.annotation.shipment;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.shipment.validator.ShipmentReasonValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione custom per la validazione delle causali di spedizione all'interno del dominio ADR.
 * <p>
 * Questa annotazione funge da <i>marker</i> per attivare il motore di validazione 
 * di Spring (Hibernate Validator) sui campi o parametri che rappresentano la causale del trasporto. 
 * La logica di validazione vera e propria è delegata alla classe {@link ShipmentReasonValidator}, 
 * che assicura che il valore fornito dal client (tipicamente tramite un payload JSON) 
 * sia non nullo e rigorosamente mappabile sui valori dell'enumerazione di dominio.
 * </p>
 * <p>
 * <b>Contesto d'uso:</b><br>
 * È progettata per essere applicata ai Data Transfer Object (DTO) o direttamente ai parametri 
 * dei RestController. Intercettando le anomalie a livello di strato Web, previene l'innesco di 
 * eccezioni di parsing nei Service e garantisce che il sistema processi solo stati logistici sicuri.
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see ShipmentReasonValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { ShipmentReasonValidator.class })
public @interface ValidatorShipmentReason {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: shipment reason is missing or unrecognized.";
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
