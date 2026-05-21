package dev.vinciguerra.adrsentinel.web.annotation.vehicle;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.validator.VehicleApprovalsValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Constraint) progettata per il controllo perimetrale 
 * (Edge Validation) sulle collezioni di omologazioni ADR associate a un veicolo.
 * Esegue un'ispezione profonda (Deep Inspection) per garantire che ogni elemento della 
 * collezione fornita sia semanticamente valido e mappabile nel dizionario di sistema.
 * <p><b>Contesto Architetturale (Deep Inspection & Type-Safety):</b></p>
 * A differenza dei validatori scalari, questa interfaccia opera su strutture dati complesse 
 * (es. {@code Set<String>}). Nel pieno rispetto dell'Anti-Corruption Layer, tollera 
 * l'assenza dell'intera collezione (payload opzionale), ma è inflessibile sul suo contenuto: 
 * se la collezione è presente, ogni singola stringa al suo interno deve corrispondere 
 * esattamente a una costante definita nell'enumerazione di dominio {@code VehicleApproval}.
 * Questo previene l'iniezione di array malformati contenenti valori nulli, vuoti o non riconosciuti.
 * La logica iterativa e di risoluzione è demandata alla classe {@link VehicleApprovalsValidator}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see VehicleApprovalsValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { VehicleApprovalsValidator.class })
public @interface ValidatorVehicleApprovals {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: provided vehicle approvals contain unrecognized or empty values.";
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
