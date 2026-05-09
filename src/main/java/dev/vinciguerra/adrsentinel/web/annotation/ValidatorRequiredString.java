package dev.vinciguerra.adrsentinel.web.annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Vincolo di validazione perimetrale centralizzato (Macro-Vincolo) per campi testuali 
 * obbligatori a lunghezza standard (massimo 255 caratteri).
 * <p><b>Contesto Architetturale (DRY & Single Source of Truth):</b></p>
 * Questa meta-annotazione rappresenta una scelta di design architetturale volta a consolidare 
 * molteplici regole di validazione identiche (es. nomi, descrizioni, stati, unità di misura) 
 * in un'unica "Fonte di Verità". Oltre a ridurre drasticamente il codice boilerplate (Annotation 
 * Explosion) all'interno dei Data Transfer Object (DTO), garantisce un comportamento 
 * omogeneo dell'intera API nella gestione degli input testuali standard.
 * <p><b>Motore di Validazione (Constraint Composition):</b></p>
 * Sfruttando il pattern della composizione dei vincoli ({@code @Constraint(validatedBy = {})}), 
 * l'annotazione orchestra simultaneamente due barriere di sicurezza:
 * <ul>
 * <li><b>Esistenza Assoluta ({@code @NotNull}):</b> Previene l'accettazione di payload 
 * mancanti o esplicitamente nulli, garantendo che il dato raggiunga il Service Layer.</li>
 * <li><b>Allineamento al Database ({@code @Size}):</b> Fissa un limite architetturale 
 * invalicabile di 255 caratteri. Questa scelta non è casuale: mappa esattamente la 
 * capienza predefinita delle colonne {@code VARCHAR(255)} nei database SQL (es. PostgreSQL, 
 * MySQL). Previene iniezioni di payload massivi (Payload Bloating) e azzera il rischio 
 * di eccezioni di <i>Data Truncation</i> (Overflow) durante i salvataggi tramite Hibernate.</li>
 * </ul>
 * <p><b>Applicabilità:</b></p>
 * Progettata per essere applicata su campi di tipo {@code String} all'interno dei DTO, o 
 * direttamente sui parametri dei Controller (es. {@code @RequestParam @ValidatorRequiredString String filter}).
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = {})
@NotNull(message = "String cannot be null")
@Size(
	max = 255,
	message = "String cannot exceed 255 characters."
)
public @interface ValidatorRequiredString {
	/**
	 * Definisce il messaggio di errore di fallback restituito al client (HTTP 400 Bad Request) 
	 * qualora la validazione fallisca e non sia stato sovrascritto al momento dell'utilizzo.
	 * @return la stringa contenente il messaggio di errore aggregato.
	 */
	String message() default "Invalid String length or missing value.";
	/**
	 * Permette di specificare i gruppi di validazione a cui appartiene questo vincolo, 
	 * utile per applicare logiche di validazione condizionale a seconda del contesto 
	 * (es. bypassare la validazione in specifiche fasi di draft).
	 * @return l'array dei gruppi di validazione (di default vuoto).
	 */
	Class<?>[] groups() default {};
	/**
	 * Permette l'assegnazione di payload personalizzati (metadati) all'errore di validazione, 
	 * utilizzabili dall'infrastruttura per mappare livelli di severità o codici di errore interni.
	 * @return l'array delle classi di payload estese (di default vuoto).
	 */
	Class<? extends Payload>[] payload() default {};
}
