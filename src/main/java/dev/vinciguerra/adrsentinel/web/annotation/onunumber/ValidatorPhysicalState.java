package dev.vinciguerra.adrsentinel.web.annotation.onunumber;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.validator.PhysicalStateValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Constraint) progettata per il controllo perimetrale 
 * (Edge Validation) sullo stato fisico della materia associato a una merce pericolosa (ADR).
 * Verifica simultaneamente la presenza del dato e la sua esatta corrispondenza con il 
 * dizionario di dominio (Enum).
 * <p><b>Contesto Architetturale (Strict Validation & Dictionary Match):</b></p>
 * Questa interfaccia applica una validazione restrittiva a protezione del layer di business. 
 * Assorbe nativamente l'obbligatorietà del dato (comportandosi come un {@code @NotBlank}) 
 * e garantisce che la stringa fornita sia mappabile in modo sicuro (Type-Safe) nell'enumerazione 
 * di sistema {@code PhysicalState} (es. SOLID, LIQUID, GAS).
 * Questo approccio previene le insidiose {@code IllegalArgumentException} a runtime durante 
 * il Data Binding o la mappatura nel Service Layer.
 * La logica ispettiva è demandata alla classe {@link PhysicalStateValidator}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see PhysicalStateValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { PhysicalStateValidator.class })
public @interface ValidatorPhysicalState {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * in caso di fallimento della validazione.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400),
	 * indicante l'assenza del dato o il mancato riconoscimento dello stato fisico.
	 */
	String message() default "Malformed payload: physical state is missing or unrecognized.";
	/**
	 * Partiziona l'esecuzione del vincolo associandolo a specifici gruppi di validazione 
	 * (Validation Groups). Utile per differenziare i controlli a seconda della casistica 
	 * operativa (es. validare il campo in POST ma ignorarlo in PATCH).
	 * @return l'array delle classi (gruppi) a cui appartiene questo vincolo (di default vuoto).
	 */
	Class<?>[] groups() default {};
	/**
	 * Consente all'architettura di sistema di allegare metadati informativi (Payload) 
	 * alla violazione del vincolo, tipicamente utilizzati per mappare l'errore su 
	 * specifici livelli di logging (INFO, WARNING, FATAL) o codici errore interni.
	 * @return l'array delle classi di payload estese (di default vuoto).
	 */
    Class<? extends Payload>[] payload() default {};
}
