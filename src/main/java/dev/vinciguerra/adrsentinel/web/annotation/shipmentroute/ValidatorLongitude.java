package dev.vinciguerra.adrsentinel.web.annotation.shipmentroute;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentroute.validator.LongitudeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Vincolo di validazione personalizzato (Custom Constraint) per le coordinate geografiche di longitudine.
 * <p>
 * Assicura che il valore numerico ({@code Double}) annotato rappresenti una <b>longitudine 
 * geograficamente e matematicamente valida</b>. Cartograficamente, la longitudine misura l'angolo 
 * in direzione Est-Ovest partendo dal Meridiano di Greenwich e deve essere rigorosamente 
 * contenuto nell'intervallo chiuso <b>[-180.0, +180.0]</b> gradi.
 * </p>
 * <p>
 * <b>Scelte Architetturali (Strict Validation Mode):</b><br>
 * A differenza delle convenzioni di tolleranza standard previste dalla specifica JSR-380, 
 * l'implementazione delegata a {@link LongitudeValidator} adotta un approccio "Strict". 
 * L'assenza del dato ({@code null}) viene attivamente intercettata e respinta come non valida. 
 * Questa scelta centralizza sia il controllo di obbligatorietà (Presence) che quello 
 * di integrità spaziale (Domain Check) all'interno di un'unica annotazione. Il validatore agisce 
 * inoltre da scudo di sicurezza contro eventuali anomalie a virgola mobile dello standard IEEE 754 
 * (valori {@code NaN} o infiniti).
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see LongitudeValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { LongitudeValidator.class })
public @interface ValidatorLongitude {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: longitude is required and must be a valid number between -180.0 and 180.0.";
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
