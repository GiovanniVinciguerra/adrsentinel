package dev.vinciguerra.adrsentinel.web.annotation.vehicle;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.validator.AxlesNumberValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Constraint) progettata per il controllo perimetrale 
 * (Edge Validation) sul numero di assi (Axle Count) dei veicoli commerciali o dei convogli 
 * nel dominio dei trasporti (ADR) e della logistica pesante.
 * Verifica simultaneamente la presenza obbligatoria del dato e la sua coerenza meccanica.
 * <p><b>Contesto Architetturale (Strict Validation & Domain-Driven Design):</b></p>
 * Questa interfaccia è la chiave di volta per i motori di calcolo della ripartizione dei pesi. 
 * Assorbendo l'obbligatorietà logica e applicando un rigoroso Boundary Check, assicura che il 
 * Service Layer processi esclusivamente autotelai fisicamente in grado di viaggiare. Il range 
 * implicito [2 - 8] assi traduce in software le limitazioni della meccanica veicolare e del 
 * Codice della Strada: partendo da un minimo assoluto di 2 assi (condizione imprescindibile per 
 * la stabilità di qualsiasi veicolo isolato), fino a un massimo di 8 assi, limite superiore che 
 * copre i trasporti eccezionali o i convogli modulari standard. Previene istantaneamente 
 * aberrazioni fisiche (es. veicoli a 0 o 1 asse) che causerebbero fatali divisioni per zero 
 * nei calcoli del peso per asse.
 * La logica ispettiva è demandata alla classe {@link AxlesNumberValidator}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see AxlesNumberValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { AxlesNumberValidator.class })
public @interface ValidatorAxlesNumber {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: the required axle count is missing or out of bounds (expected 2 - 8 axles).";
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
