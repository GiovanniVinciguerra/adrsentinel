package dev.vinciguerra.adrsentinel.web.annotation.shipment;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.shipment.validator.DistanceValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Constraint) progettata per il controllo perimetrale 
 * (Edge Validation) sulle misurazioni di distanza nel dominio applicativo (es. chilometraggio, 
 * raggio di trasporto).
 * Verifica simultaneamente la presenza obbligatoria del dato e la sua assoluta coerenza matematica.
 * <p><b>Contesto Architetturale (Strict Validation & Mathematical Sanitization):</b></p>
 * Questa interfaccia agisce come uno scudo matematico a protezione dell'Anti-Corruption Layer. 
 * Assorbe l'obbligatorietà del parametro (rifiutando i valori {@code null}) e garantisce che 
 * il Service Layer riceva esclusivamente numeri a virgola mobile (Float/Double) reali, 
 * finiti e strettamente positivi. Questo previene iniezioni di valori anomali come {@code NaN} 
 * o {@code Infinity}, che causerebbero la corruzione delle formule di calcolo a valle 
 * o il collasso delle query sul database.
 * La logica ispettiva e di sanitizzazione è demandata alla classe {@link DistanceValidator}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see DistanceValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { DistanceValidator.class })
public @interface ValidatorDistance {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: the required numeric value is missing or invalid (expected a strictly positive, finite number).";
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
