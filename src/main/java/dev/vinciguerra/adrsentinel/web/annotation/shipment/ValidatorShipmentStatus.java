package dev.vinciguerra.adrsentinel.web.annotation.shipment;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.shipment.validator.ShipmentStatusValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Constraint) progettata per il controllo perimetrale 
 * (Edge Validation) sullo Stato della Spedizione (Shipment Status) nel dominio trasporti.
 * Verifica simultaneamente la presenza obbligatoria del dato e la sua esatta corrispondenza 
 * con il dizionario di sistema (Enum).
 * <p><b>Contesto Architetturale (Strict Validation & Type-Safety):</b></p>
 * Questa interfaccia agisce come un validatore restrittivo a protezione dell'Anti-Corruption Layer. 
 * Fondendo l'obbligatorietà logica (comportandosi come un {@code @NotNull}) con una rigorosa 
 * verifica semantica (Enum Matching), garantisce che il Service Layer riceva esclusivamente 
 * transizioni di stato legali e riconosciute (es. PENDING, IN_TRANSIT, DELIVERED).
 * Questo approccio blocca alla frontiera payload incompleti o contenenti stati inventati dal client.
 * La logica ispettiva è demandata alla classe {@link ShipmentStatusValidator}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see ShipmentStatusValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { ShipmentStatusValidator.class })
public @interface ValidatorShipmentStatus {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: shipment status is missing or unrecognized.";
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
