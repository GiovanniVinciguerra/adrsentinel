package dev.vinciguerra.adrsentinel.web.annotation.vehicle;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.validator.LengthValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Constraint) progettata per il controllo perimetrale 
 * (Edge Validation) sull'ingombro longitudinale (lunghezza) di veicoli, rimorchi o unità di carico 
 * nel dominio dei trasporti commerciali e ADR.
 * Verifica simultaneamente la presenza obbligatoria del dato, la sua finitezza matematica 
 * e il rispetto dei limiti fisici e legali di sagoma stradale.
 * <p><b>Contesto Architetturale (Strict Validation & Domain-Driven Design):</b></p>
 * Questa interfaccia completa la matrice dimensionale 3D del veicolo, codificando i limiti 
 * imposti dal Codice della Strada in ferree regole software. Assorbendo l'obbligatorietà 
 * logica e applicando un rigoroso Boundary Check, garantisce che i sistemi di routing e di 
 * prenotazione slot non debbano mai gestire misure paradossali. Il range implicito 
 * [3.4 - 18.75] (metri) copre l'intero spettro operativo: dallo sbarramento inferiore per 
 * i mezzi leggeri fino all'assoluto limite legale europeo di 18.75 metri per gli autotreni 
 * (motrice + rimorchio).
 * La logica ispettiva è demandata alla classe {@link LengthValidator}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see LengthValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { LengthValidator.class })
public @interface ValidatorLength {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: the required numeric value is missing, invalid, or out of bounds (expected a finite float between 3.4 and 18.75).";
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
