package dev.vinciguerra.adrsentinel.web.annotation.shipmentroute;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentroute.validator.ETAValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Vincolo di validazione personalizzato (Custom Constraint) per i tempi stimati di percorrenza 
 * (ETA - Estimated Time of Arrival) espressi in minuti.
 * <p>
 * Assicura che il valore numerico ({@code Integer}) annotato rappresenti un orizzonte temporale 
 * di viaggio logicamente e fisicamente valido per il dominio logistico. 
 * </p>
 * <p>
 * <b>Scelte Architetturali e di Sicurezza (Defensive Programming):</b><br>
 * <ul>
 * <li><b>Strict Nullability:</b> Il validatore delegato ({@link ETAValidator}) intercetta e respinge 
 * attivamente l'assenza del dato ({@code null}), rendendo il campo intrinsecamente obbligatorio.</li>
 * <li><b>Limiti Inferiori:</b> Impedisce l'inserimento di tempi negativi o pari a zero, prevenendo 
 * anomalie logiche come viaggi istantanei o paradossi temporali.</li>
 * <li><b>Sanity Check (Limite Superiore):</b> Fissa un tetto massimo invalicabile di 43.200 minuti 
 * (pari a 30 giorni ininterrotti). Questo limite salvaguarda l'integrità dei sistemi a valle, 
 * prevenendo attacchi malevoli o bug dei motori di routing (es. OpenRouteService) che potrebbero 
 * causare {@code Integer Overflow} durante le successive manipolazioni delle date 
 * (es. {@code LocalDateTime.plusMinutes()}).</li>
 * </ul>
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see ETAValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { ETAValidator.class })
public @interface ValidatorETA {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (nel payload HTTP 400 Bad Request) 
	 * qualora il valore temporale risulti assente, negativo, nullo o superiore al tetto massimo consentito (30 giorni).
	 * @return la stringa contenente il messaggio formattato secondo lo standard Minimalist REST.
	 */
	String message() default "Malformed payload: ETA is required and must be between 1 and 43200 minutes.";
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
