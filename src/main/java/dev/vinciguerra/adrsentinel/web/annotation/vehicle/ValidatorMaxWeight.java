package dev.vinciguerra.adrsentinel.web.annotation.vehicle;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.validator.MaxWeightValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Constraint) progettata per il controllo perimetrale 
 * (Edge Validation) sul peso massimo autorizzato o sulla massa lorda dei veicoli nel dominio 
 * dei trasporti commerciali (ADR).
 * Verifica simultaneamente la presenza obbligatoria del dato e il rispetto dei limiti 
 * fisici e normativi di operatività stradale.
 * <p><b>Contesto Architetturale (Strict Validation & Domain-Driven Design):</b></p>
 * Questa interfaccia traduce i vincoli fisici del mondo reale in regole software inossidabili. 
 * Assorbendo l'obbligatorietà logica (comportandosi come un {@code @NotNull}) e applicando 
 * un rigoroso Boundary Check, assicura che il Service Layer operi esclusivamente su 
 * grandezze realistiche. Il range implicito [1500 - 44000] kg rappresenta lo spettro dei 
 * veicoli commerciali su gomma, dai furgoni leggeri agli autoarticolati a pieno carico. 
 * Questo design pattern previene alla radice i catastrofici errori di scala dimensionale 
 * (es. l'inserimento del peso in tonnellate o grammi al posto dei chilogrammi).
 * La logica ispettiva è demandata alla classe {@link MaxWeightValidator}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see MaxWeightValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { MaxWeightValidator.class })
public @interface ValidatorMaxWeight {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: the required numeric value is missing or out of bounds (expected between 1500 and 44000 (Kg)).";
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
