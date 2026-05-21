package dev.vinciguerra.adrsentinel.web.annotation.adrclass;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.adrclass.validator.AdrDescriptionValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Constraint) progettata per il controllo perimetrale 
 * (Edge Validation) sulle descrizioni testuali delle classi di pericolo ADR.
 * Verifica simultaneamente la presenza obbligatoria del dato e i suoi vincoli dimensionali.
 * <p><b>Contesto Architetturale (Composite Validation & Domain-Driven Design):</b></p>
 * Questa interfaccia agisce come un validatore composito. Condensa in un'unica regola di 
 * business semantica le logiche infrastrutturali che tradizionalmente richiederebbero 
 * l'uso congiunto di più annotazioni (es. {@code @NotBlank} e {@code @Size(min=3, max=255)}). 
 * Mantiene i Data Transfer Object (DTO) puliti e focalizzati sul Dominio, garantendo che 
 * il Service Layer riceva esclusivamente stringhe strutturalmente valide.
 * La logica di validazione restrittiva è delegata alla classe {@link AdrDescriptionValidator}.
 * <p><b>Esempio d'uso:</b></p>
 * <pre>
 * {@code
 * public record AdrClassRequestDTO(
 * @ValidatorAdrClassCode String code,
 * @ValidatorAdrDescription String description
 * ) {}
 * }
 * </pre>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see AdrDescriptionValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { AdrDescriptionValidator.class })
public @interface ValidatorAdrDescription {
	/**
	 * Definisce il messaggio di errore predefinito restituito al client (HTTP 400) 
	 * qualora il valore fornito non rispetti il formato ADR per la descrizione.
	 * @return la stringa contenente il messaggio di errore.
	 */
	String message() default "Malformed payload: ADR description is missing or invalid (expected 3-255 characters).";
	/**
	 * Permette di specificare i gruppi di validazione a cui appartiene questo vincolo.
	 * Utilizzato per applicare logiche di validazione condizionale (es. validare diversamente 
	 * in fase di Creazione rispetto alla fase di Aggiornamento).
	 * @return l'array dei gruppi di validazione (di default vuoto).
	 */
	Class<?>[] groups() default {};
	/**
	 * Permette all'architettura di sistema di assegnare payload personalizzati (metadati) 
	 * all'errore di validazione (es. livelli di severità come INFO, WARNING, FATAL).
	 * @return l'array delle classi di payload estese (di default vuoto).
	 */
	Class<? extends Payload>[] payload() default {};
}
