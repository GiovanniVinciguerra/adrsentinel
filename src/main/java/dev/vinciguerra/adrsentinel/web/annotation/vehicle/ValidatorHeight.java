package dev.vinciguerra.adrsentinel.web.annotation.vehicle;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.validator.HeightValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Constraint) progettata per il controllo perimetrale 
 * (Edge Validation) sull'ingombro verticale (altezza) di veicoli, container o unità di carico 
 * nel dominio dei trasporti commerciali e ADR.
 * Verifica simultaneamente la presenza obbligatoria del dato, la sua finitezza matematica 
 * e il rispetto dei limiti fisici e stradali.
 * <p><b>Contesto Architetturale (Strict Validation & Domain-Driven Design):</b></p>
 * Questa interfaccia plasma le leggi della fisica e della strada in regole software inossidabili. 
 * Assorbendo l'obbligatorietà logica e applicando un rigoroso Boundary Check, assicura che il 
 * Service Layer non processi mai dimensioni surreali. Il range implicito [1.7 - 4.3] (metri) 
 * rappresenta lo spettro operativo reale dei trasporti su gomma: partendo dai piccoli veicoli 
 * commerciali leggeri (~1.7m), fino ad arrivare al limite massimo europeo per i rimorchi 
 * "Mega" o trasporti eccezionali standardizzati (~4.3m). Questo previene errori critici di 
 * inserimento (es. altezza inserita in centimetri al posto dei metri) che corromperebbero 
 * gli algoritmi di routing (es. calcolo dei percorsi sotto i ponti).
 * La logica ispettiva è demandata alla classe {@link HeightValidator}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see HeightValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { HeightValidator.class })
public @interface ValidatorHeight {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: the required numeric value is missing, invalid, or out of bounds (expected a finite float between 1.7 and 4.3).";
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
