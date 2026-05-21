package dev.vinciguerra.adrsentinel.web.annotation.vehicle;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.validator.MaxUsefulWeightValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Constraint) progettata per il controllo perimetrale 
 * (Edge Validation) sulla Portata Utile Massima (Max Useful Weight) di veicoli o imballaggi 
 * nel dominio dei trasporti (ADR).
 * Verifica simultaneamente la presenza obbligatoria del dato e la sua coerenza fisica 
 * (valore intero strettamente positivo).
 * <p><b>Contesto Architetturale (Strict Validation & Domain-Driven Design):</b></p>
 * Questa interfaccia è un tassello fondamentale per garantire l'integrità dei motori di 
 * calcolo logistici. Assorbendo l'obbligatorietà logica (comportandosi come un {@code @NotNull}) 
 * e bloccando i valori nulli o negativi, assicura che il Service Layer non incontri mai 
 * paradossi fisici (es. un mezzo di trasporto privo di capacità di carico). Questo previene 
 * iniezioni di anomalie matematiche come la divisione per zero durante gli algoritmi di 
 * ripartizione della merce.
 * La logica ispettiva e di sanitizzazione è demandata alla classe {@link MaxUsefulWeightValidator}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see MaxUsefulWeightValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { MaxUsefulWeightValidator.class })
public @interface ValidatorMaxUsefulWeight {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: the required numeric value is missing or invalid (expected a strictly positive integer).";
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
