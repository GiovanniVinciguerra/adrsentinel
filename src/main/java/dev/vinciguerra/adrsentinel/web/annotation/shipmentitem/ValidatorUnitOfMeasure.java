package dev.vinciguerra.adrsentinel.web.annotation.shipmentitem;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.validator.UnitOfMeasureValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Constraint) progettata per il controllo perimetrale 
 * (Edge Validation) sull'Unità di Misura (Unit of Measure) nel dominio applicativo ADR.
 * Verifica simultaneamente la presenza obbligatoria del dato e la sua esatta corrispondenza 
 * con il dizionario di sistema (Enum).
 * <p><b>Contesto Architetturale (Strict Validation & Domain-Driven Design):</b></p>
 * Questa interfaccia rappresenta un pilastro del Domain-Driven Design all'interno 
 * dell'Anti-Corruption Layer. Agendo in stretta sinergia con i validatori numerici 
 * (es. {@code @ValidatorQuantity}), garantisce che il sistema non soffra mai di 
 * "Primitive Obsession". Assorbendo l'obbligatorietà logica (comportandosi come un 
 * {@code @NotNull}) e applicando una rigorosa Type-Safety, assicura che il Service Layer 
 * riceva solo grandezze fisiche complete (Valore + Unità di Misura legale, es. KG, L, T), 
 * respingendo payload orfani o contenenti sigle inventate dal client.
 * La logica ispettiva è demandata alla classe {@link UnitOfMeasureValidator}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see UnitOfMeasureValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { UnitOfMeasureValidator.class })
public @interface ValidatorUnitOfMeasure {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: the required unit of measure is missing or unrecognized.";
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
