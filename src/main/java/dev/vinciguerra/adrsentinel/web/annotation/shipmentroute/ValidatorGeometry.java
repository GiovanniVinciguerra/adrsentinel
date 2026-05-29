package dev.vinciguerra.adrsentinel.web.annotation.shipmentroute;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentroute.validator.GeometryValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Vincolo di validazione personalizzato (Custom Constraint) per le stringhe di geometria vettoriale 
 * (Cartographic Polyline).
 * <p>
 * Questa annotazione garantisce che la stringa deputata a tracciare la rotta su mappa sia 
 * strutturalmente intatta, sicura e conforme all'algoritmo matematico <b>Encoded Polyline Algorithm</b>.
 * </p>
 * <p>
 * <b>Responsabilità Architetturali e di Sicurezza (Defensive Shield):</b>
 * <ul>
 * <li><b>Strict Presence:</b> Rende il campo intrinsecamente obbligatorio, respingendo valori 
 * {@code null}, stringhe vuote o sequenze composte da soli spazi bianchi (Blank).</li>
 * <li><b>DoS / Payload Bomb Protection:</b> Agisce in tandem con le restrizioni del {@code JsonMapper} 
 * per imporre un tetto massimo invalicabile di 1.000.000 di caratteri (~1 MB). Questo protegge il 
 * server da attacchi di saturazione della memoria (Out-Of-Memory) mantenendo un margine 
 * sufficientemente ampio (x2) per le tratte logistiche paneuropee più complesse.</li>
 * <li><b>Sanitization (Anti-XSS):</b> Il validatore delegato ({@link GeometryValidator}) assicura 
 * tramite espressione regolare che la stringa contenga esclusivamente i byte ASCII validi per la 
 * cifratura spaziale (dal carattere '?' alla '~'), bloccando sul nascere spazi spuri, ritorni 
 * a capo o potenziali script malevoli iniettati dal client.</li>
 * </ul>
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see GeometryValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { GeometryValidator.class })
public @interface ValidatorGeometry {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (nel payload HTTP 400 Bad Request) 
	 * qualora la stringa geometrica risulti assente, superi il limite vitale di 1.000.000 di caratteri 
	 * o presenti una formattazione crittografica corrotta.
	 * @return la stringa contenente il messaggio formattato secondo lo standard Minimalist REST.
	 */
	String message() default "Malformed payload: geometry is required, must not exceed 1,000,000 characters, and must be a valid encoded Polyline.";
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
