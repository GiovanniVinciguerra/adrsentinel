package dev.vinciguerra.adrsentinel.web.annotation.compatibilityrules;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.compatibilityrules.validator.WarningNoteValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Constraint) progettata per il controllo perimetrale 
 * (Edge Validation) sulle note di avvertenza (Warning Notes) o annotazioni testuali libere.
 * Verifica che il testo, qualora fornito, rispetti rigorosamente i vincoli dimensionali di sistema.
 * <p><b>Contesto Architetturale (Single Responsibility & Optionality):</b></p>
 * Questa interfaccia gestisce esclusivamente i limiti di archiviazione e tollera l'assenza del dato. 
 * Se la nota di avvertenza rappresenta un campo opzionale nel Dominio, questa singola 
 * annotazione è sufficiente. Qualora il campo debba essere reso obbligatorio per specifici 
 * payload, andrà affiancata a un'annotazione di presenza strutturale.
 * La logica di validazione è delegata alla classe {@link WarningNoteValidator}
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see WarningNoteValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { WarningNoteValidator.class })
public @interface ValidatorWarningNote {
	/**
	 * Definisce il messaggio di errore predefinito restituito al client (REST Payload) 
	 * qualora la nota di avvertenza, seppur presente, violi i limiti di lunghezza previsti.
	 * @return la stringa contenente il messaggio di errore formattato secondo lo standard Minimalist REST.
	 */
	String message() default "Malformed payload: invalid length for the provided value (expected 3-255 characters).";
	/**
	 * Permette di specificare i gruppi di validazione a cui appartiene questo vincolo.
	 * Utilizzato per applicare logiche di validazione condizionale (es. eseguire controlli 
	 * differenti in base a specifiche fasi del ciclo di vita della richiesta).
	 * @return l'array dei gruppi di validazione (di default vuoto).
	 */
	Class<?>[] groups() default {};
	/**
	 * Permette all'architettura di sistema di assegnare payload personalizzati (metadati) 
	 * all'errore di validazione (es. integrazione con sistemi di logging centralizzati 
	 * per tracciare livelli di severità come INFO, WARNING, FATAL).
	 * @return l'array delle classi di payload estese (di default vuoto).
	 */
    Class<? extends Payload>[] payload() default {};
}
