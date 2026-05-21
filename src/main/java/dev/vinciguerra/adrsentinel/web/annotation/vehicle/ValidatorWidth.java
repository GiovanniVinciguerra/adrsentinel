package dev.vinciguerra.adrsentinel.web.annotation.vehicle;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.validator.WidthValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Constraint) progettata per il controllo perimetrale 
 * (Edge Validation) sull'ingombro trasversale (larghezza) di veicoli, container o unità di carico 
 * nel dominio dei trasporti commerciali e ADR.
 * Verifica simultaneamente la presenza obbligatoria del dato, la sua finitezza matematica 
 * e il rispetto dei limiti fisici e legali di sagoma stradale.
 * <p><b>Contesto Architetturale (Strict Validation & Domain-Driven Design):</b></p>
 * Questa interfaccia recepisce le direttive stradali europee sui limiti di sagoma e le traduce 
 * in regole software inossidabili. Assorbendo l'obbligatorietà logica e applicando un rigoroso 
 * Boundary Check, assicura che il Service Layer non processi mai dimensioni surreali. 
 * Il range implicito [1.5 - 2.6] (metri) riflette la realtà operativa dei trasporti su gomma: 
 * partendo dai piccoli mezzi commerciali leggeri (~1.5m), fino ad arrivare al limite massimo 
 * legale assoluto per i veicoli a temperatura controllata (isotermici/ATP) pari a 2.6 metri. 
 * Questo previene errori macroscopici di input (es. larghezza inserita in centimetri o millimetri 
 * al posto dei metri) prima che colpiscano la persistenza o i sistemi di routing.
 * La logica ispettiva è demandata alla classe {@link WidthValidator}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see WidthValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { WidthValidator.class })
public @interface ValidatorWidth {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: the required width is missing or out of bounds (expected 1.5 - 2.6 meters).";
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
