package dev.vinciguerra.adrsentinel.web.annotation.onunumber;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.validator.PackingGroupValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Constraint) progettata per il controllo perimetrale 
 * (Edge Validation) sul Gruppo di Imballaggio (Packing Group) associato alle merci ADR.
 * Verifica che il dato, qualora fornito dal client, corrisponda esattamente a uno dei 
 * valori predefiniti nel dizionario di dominio (Enum).
 * <p><b>Contesto Architetturale (Optionality & Type-Safety):</b></p>
 * Nel pieno rispetto del <i>Single Responsibility Principle</i>, questa interfaccia 
 * tollera nativamente l'assenza del dato (valori {@code null}), riconoscendo che 
 * non tutte le merci pericolose ADR possiedono un gruppo di imballaggio. Tuttavia, se 
 * il dato è presente, garantisce che sia mappabile in modo sicuro (Type-Safe) 
 * nell'enumerazione {@code PackingGroup} (es. I, II, III). Qualora il caso d'uso specifico 
 * richieda l'obbligatorietà del campo, questa annotazione andrà composta con un {@code @NotNull}.
 * La logica ispettiva è demandata alla classe {@link PackingGroupValidator}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see PackingGroupValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { PackingGroupValidator.class })
public @interface ValidatorPackingGroup {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora il gruppo di imballaggio fornito non sia riconosciuto a sistema.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: the provided packing group is unrecognized.";
	/**
	 * Partiziona l'esecuzione del vincolo associandolo a specifici gruppi di validazione 
	 * (Validation Groups).
	 * @return l'array delle classi (gruppi) a cui appartiene questo vincolo (di default vuoto).
	 */
	Class<?>[] groups() default {};
	/**
	 * Consente all'architettura di sistema di allegare metadati informativi (Payload) 
	 * alla violazione del vincolo, utili per tracciare livelli di severità (es. WARNING, FATAL).
	 * @return l'array delle classi di payload estese (di default vuoto).
	 */
	Class<? extends Payload>[] payload() default {};
}
