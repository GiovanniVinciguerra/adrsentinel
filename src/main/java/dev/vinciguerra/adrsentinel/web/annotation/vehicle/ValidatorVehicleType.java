package dev.vinciguerra.adrsentinel.web.annotation.vehicle;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.validator.VehicleTypeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Constraint) progettata per il controllo perimetrale 
 * (Edge Validation) sulla Tassonomia di Dominio relativa al tipo di veicolo (Vehicle Type) 
 * nel contesto logistico e dei trasporti ADR.
 * <p><b>Contesto Architetturale (String-to-Enum Safe Binding):</b></p>
 * Questa interfaccia implementa un pattern di sicurezza vitale per le API REST. Invece di 
 * forzare il framework di serializzazione (es. Jackson) a mappare direttamente il payload JSON 
 * su una enumerazione Java — con il rischio di generare letali {@code HttpMessageNotReadableException} 
 * e stack-trace esposti al client in caso di typo — l'architettura accetta una {@code String} cruda. 
 * Il validatore associato si occupa di tentare il binding controllato con l'enumerazione 
 * {@code VehicleType}. Questo approccio garantisce che la validazione della tassonomia rimanga 
 * saldamente all'interno del layer di validazione, permettendo al sistema di restituire 
 * un errore HTTP 400 elegante, standardizzato e in standard "Minimalist REST".
 * La logica ispettiva e di mapping è demandata alla classe {@link VehicleTypeValidator}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see VehicleTypeValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { VehicleTypeValidator.class })
public @interface ValidatorVehicleType {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: the required vehicle type is missing or not recognized.";
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
